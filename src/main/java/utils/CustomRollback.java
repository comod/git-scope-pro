package utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeImpl;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.components.JBScrollPane;
import git4idea.repo.GitRepository;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.Git;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.application.WriteAction;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class CustomRollback {

    public void rollbackChanges(@NotNull Project project, @NotNull Change[] changes, String revisionString) {
        AsyncChangesTreeImpl.Changes changesTree = new AsyncChangesTreeImpl.Changes(
                project,
                true,
                true,
                Arrays.asList(changes)
        );

        changesTree.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changesTree.setEmptyText("No changes to rollback");

        JScrollPane scrollPane = new JBScrollPane(changesTree);
        scrollPane.setPreferredSize(new Dimension(800, 400));

        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.add(scrollPane, BorderLayout.CENTER);

        String dialogTitle = revisionString != null && !revisionString.isEmpty()
                ? "Rollback Selected Changes to '" + revisionString + "'"
                : "Rollback Selected Changes";

        DialogWrapper dialog = new DialogWrapper(project, true) {
            {
                setTitle(dialogTitle);
                init();
            }
            @Override
            protected JComponent createCenterPanel() {
                return treePanel;
            }
            @Override
            public JComponent getPreferredFocusedComponent() {
                return changesTree;
            }
        };

        dialog.show();

        if (dialog.isOK()) {
            List<Change> includedChanges = new ArrayList<>(changesTree.getIncludedChanges());

            List<String> uncommittedFiles = getUncommittedFiles(project, includedChanges);

            if (!uncommittedFiles.isEmpty()) {
                boolean confirmed = showConfirmDialogWithFiles(
                        project,
                        "Changes contain uncommitted modifications. Confirm rollback?",
                        uncommittedFiles,
                        Messages.getWarningIcon()
                );
                if (!confirmed) {
                    return;
                }
            }

            new Task.Backgroundable(project, "Rolling back selected changes", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(false);

                    String suffix = (revisionString != null && !revisionString.isEmpty())
                            ? " to '" + revisionString + "'" : "";
                    LocalHistory.getInstance().putSystemLabel(project, "Git Scope Rollback" + suffix);

                    // Background Git operations
                    List<String> failedFiles = revertToBeforeRev(project, includedChanges, indicator);
                    if (!failedFiles.isEmpty()) {
                        showFailedFilesDialog(project, failedFiles);
                    }
                }
            }.queue();
        }
    }

    private List<String> getUncommittedFiles(@NotNull Project project, @NotNull List<Change> changes) {
        List<String> uncommittedFiles = new ArrayList<>();
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        for (Change change : changes) {
            FilePath afterPath = ChangesUtil.getAfterPath(change);
            if (afterPath != null) {
                VirtualFile vf = afterPath.getVirtualFile();
                if (vf != null) {
                    FileStatus status = changeListManager.getStatus(vf);
                    if (status == FileStatus.MODIFIED || status == FileStatus.MERGED_WITH_CONFLICTS || status == FileStatus.ADDED) {
                        uncommittedFiles.add(afterPath.getName());
                    }
                }
            }
        }
        return uncommittedFiles;
    }

    private boolean showConfirmDialogWithFiles(@NotNull Project project, @NotNull String message, @NotNull List<String> files, Icon icon) {
        final boolean[] result = new boolean[1];

        Runnable show = () -> {
            JPanel panel = new JPanel(new BorderLayout(0, 10));
            JLabel label = new JLabel(message, icon, JLabel.LEFT);
            panel.add(label, BorderLayout.NORTH);

            JTextArea textArea = new JTextArea(String.join("\n", files));
            textArea.setEditable(false);
            textArea.setRows(Math.min(files.size(), 15));
            JScrollPane scrollPane = new JBScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(500, Math.min(300, files.size() * 20)));
            panel.add(scrollPane, BorderLayout.CENTER);

            DialogWrapper confirmDialog = new DialogWrapper(project, true) {
                {
                    setTitle(icon == Messages.getWarningIcon() ? "Confirm Rollback" : "Rollback Failed");
                    init();
                }
                @Override
                protected JComponent createCenterPanel() {
                    return panel;
                }
                @Override
                protected Action @NotNull [] createActions() {
                    if (icon == Messages.getWarningIcon()) {
                        return super.createActions(); // OK/Cancel
                    } else {
                        return new Action[]{getOKAction()};
                    }
                }
            };
            confirmDialog.show();
            result[0] = confirmDialog.isOK();
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            show.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(show);
        }
        return result[0];
    }

    private void showFailedFilesDialog(@NotNull Project project, @NotNull List<String> failedFiles) {
        ApplicationManager.getApplication().invokeLater(() -> {
            showConfirmDialogWithFiles(
                    project,
                    "Failed to rollback the following files:",
                    failedFiles,
                    Messages.getErrorIcon()
            );
        });
    }

    // Added ProgressIndicator parameter for better UX
    public List<String> revertToBeforeRev(@NotNull Project project, @NotNull List<Change> changes, @NotNull ProgressIndicator indicator) {
        List<String> failedFiles = new ArrayList<>();

        Map<VirtualFile, List<Change>> rootToChanges = new LinkedHashMap<>();
        List<GitRepository> allRepos = git4idea.GitUtil.getRepositoryManager(project).getRepositories();

        for (Change change : changes) {
            com.intellij.openapi.progress.ProgressManager.checkCanceled();
            indicator.checkCanceled();

            FilePath afterPath = ChangesUtil.getAfterPath(change);
            FilePath beforePath = ChangesUtil.getBeforePath(change);
            FilePath repoLookup = afterPath != null ? afterPath : beforePath;
            if (repoLookup == null) {
                failedFiles.add("unknown (no path)");
                continue;
            }

            String absPathNorm = FileUtil.toSystemIndependentName(repoLookup.getPath());
            GitRepository repoForChange = null;
            for (GitRepository repo : allRepos) {
                String rootPathNorm = FileUtil.toSystemIndependentName(repo.getRoot().getPath());
                if (FileUtil.pathsEqual(absPathNorm, rootPathNorm) || FileUtil.isAncestor(rootPathNorm, absPathNorm, false)) {
                    repoForChange = repo;
                    break;
                }
            }
            if (repoForChange == null) {
                failedFiles.add(absPathNorm);
                continue;
            }
            rootToChanges.computeIfAbsent(repoForChange.getRoot(), k -> new ArrayList<>()).add(change);
        }

        int total = rootToChanges.values().stream().mapToInt(List::size).sum();
        int processed = 0;

        for (Map.Entry<VirtualFile, List<Change>> entry : rootToChanges.entrySet()) {
            com.intellij.openapi.progress.ProgressManager.checkCanceled();
            indicator.checkCanceled();

            VirtualFile root = entry.getKey();
            String rootPathNorm = FileUtil.toSystemIndependentName(root.getPath());
            List<Change> repoChanges = entry.getValue();

            // Collect NEW files to batch-delete with a single git rm
            List<String> newRelPaths = new ArrayList<>();
            Map<Change, String> relAfterMap = new HashMap<>();
            Map<Change, String> relBeforeMap = new HashMap<>();

            // Precompute rel paths using FileUtil.getRelativePath
            for (Change c : repoChanges) {
                FilePath a = ChangesUtil.getAfterPath(c);
                FilePath b = ChangesUtil.getBeforePath(c);
                String relA = null, relB = null;

                if (a != null && !a.isDirectory()) {
                    String absA = FileUtil.toSystemIndependentName(a.getPath());
                    relA = FileUtil.getRelativePath(rootPathNorm, absA, '/');
                }
                if (b != null && !b.isDirectory()) {
                    String absB = FileUtil.toSystemIndependentName(b.getPath());
                    relB = FileUtil.getRelativePath(rootPathNorm, absB, '/');
                }

                if (relA != null) relAfterMap.put(c, relA);
                if (relB != null) relBeforeMap.put(c, relB);
                if (c.getType() == Change.Type.NEW && relA != null) {
                    newRelPaths.add(relA);
                }
            }

            // Track changed paths (optional; no explicit refresh below)
            Set<String> pathsToRefresh = new HashSet<>();

            // Batch rm for NEW files
            if (!newRelPaths.isEmpty()) {
                indicator.setText("Removing newly added files");
                try {
                    GitLineHandler rm = new GitLineHandler(project, root, GitCommand.RM);
                    rm.addParameters("-f", "-q", "--");
                    for (String p : newRelPaths) {
                        rm.addParameters(p);
                    }
                    Git.getInstance().runCommand(rm).throwOnError();
                    pathsToRefresh.addAll(newRelPaths);
                } catch (VcsException e) {
                    // Fall back to per-file rm if batch fails
                    for (String p : newRelPaths) {
                        try {
                            GitLineHandler rm = new GitLineHandler(project, root, GitCommand.RM);
                            rm.addParameters("-f", "-q", "--", p);
                            Git.getInstance().runCommand(rm).throwOnError();
                            pathsToRefresh.add(p);
                        } catch (VcsException ex) {
                            failedFiles.add(root.getName() + ": " + p);
                        }
                    }
                }
            }

            // Process non-NEW changes and any NEW that weren’t included above (e.g., rel path null)
            for (Change change : repoChanges) {
                indicator.checkCanceled();
                processed++;
                indicator.setFraction(total == 0 ? 1.0 : (double) processed / total);

                FilePath afterPath = ChangesUtil.getAfterPath(change);
                FilePath beforePath = ChangesUtil.getBeforePath(change);
                String relAfter = relAfterMap.get(change);
                String relBefore = relBeforeMap.get(change);

                Consumer<String> fail = p -> failedFiles.add((p != null && !p.isEmpty()) ? (root.getName() + ": " + p) : "unknown");

                // Skip directories and empty targets
                if ((afterPath != null && afterPath.isDirectory()) || (beforePath != null && beforePath.isDirectory())) {
                    continue;
                }

                // Skip NEW already handled in batch
                if (change.getType() == Change.Type.NEW) {
                    if (relAfter == null) fail.accept(afterPath != null ? afterPath.getName() : null);
                    continue;
                }

                try {
                    switch (change.getType()) {
                        case MODIFICATION: {
                            rollbackModification(project, root, pathsToRefresh, change, relAfter, relBefore, fail);
                            break;
                        }
                        case DELETED: {
                            String rev = change.getBeforeRevision() != null
                                    ? change.getBeforeRevision().getRevisionNumber().asString() : null;
                            if (rev == null || relBefore == null) { fail.accept(relBefore); break; }
                            gitCheckout(project, root, pathsToRefresh, fail, rev, relBefore);
                            break;
                        }
                        case MOVED: {
                            // Revert rename: delete new path if present, then restore the old path.
                            String rev = change.getBeforeRevision() != null
                                    ? change.getBeforeRevision().getRevisionNumber().asString() : null;
                            boolean rmFailed = false;
                            boolean checkoutFailed = false;

                            if (relAfter != null) {
                                try {
                                    GitLineHandler rm = new GitLineHandler(project, root, GitCommand.RM);
                                    rm.addParameters("-f", "-q", "--", relAfter);
                                    Git.getInstance().runCommand(rm).throwOnError();
                                    pathsToRefresh.add(relAfter);
                                } catch (VcsException e) {
                                    rmFailed = true; // might be untracked; we won’t fail if checkout succeeds
                                }
                            }
                            if (rev == null || relBefore == null) {
                                fail.accept(relBefore != null ? relBefore : relAfter);
                                break;
                            }
                            try {
                                GitLineHandler co = new GitLineHandler(project, root, GitCommand.CHECKOUT);
                                co.addParameters("--force", "-q", rev, "--", relBefore);
                                Git.getInstance().runCommand(co).throwOnError();
                                pathsToRefresh.add(relBefore);
                            } catch (VcsException e) {
                                checkoutFailed = true;
                            }
                            if (checkoutFailed) {
                                fail.accept(relBefore);
                            } else if (rmFailed && relAfter != null) {
                                // Optional: best-effort check only
                            }
                            break;
                        }
                        default: {
                            // Fallback: treat as modification
                            rollbackModification(project, root, pathsToRefresh, change, relAfter, relBefore, fail);
                            break;
                        }
                    }
                } catch (Throwable t) {
                    fail.accept(relAfter != null ? relAfter : relBefore);
                }
            }

            // No explicit VFS refresh: rely on IDE/VCS to pick up changes
            // (pathsToRefresh kept for future use if needed)
        }

        return failedFiles;
    }

    private void rollbackModification(@NotNull Project project,
                                  VirtualFile root,
                                  Set<String> pathsToRefresh,
                                  Change change,
                                  String relAfter,
                                  String relBefore,
                                  Consumer<String> fail) {
        String rev = change.getBeforeRevision() != null
                ? change.getBeforeRevision().getRevisionNumber().asString() : null;
        String target = (relAfter != null) ? relAfter : relBefore;
        if (rev == null || target == null) {
            fail.accept(target);
            return;
        }
        gitCheckout(project, root, pathsToRefresh, fail, rev, target);
    }

    private void gitCheckout(@NotNull Project project,
                             @NotNull VirtualFile root,
                             @NotNull Set<String> pathsToRefresh,
                             @NotNull Consumer<String> fail,
                             @NotNull String rev,
                             @NotNull String targetRelPath) {
        if (targetRelPath.isEmpty()) {
            fail.accept(targetRelPath);
            return;
        }
        try {
            GitLineHandler handler = new GitLineHandler(project, root, GitCommand.CHECKOUT);
            handler.addParameters("--force", "-q", rev, "--", targetRelPath);
            Git.getInstance().runCommand(handler).throwOnError();
            pathsToRefresh.add(targetRelPath);
        } catch (VcsException e) {
            fail.accept(targetRelPath);
        }
    }
}