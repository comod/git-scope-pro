
package utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class RevertToGitReferenceDialog extends DialogWrapper {

    private final Project project;
    private final List<String> filePaths;
    private ChangesTree changesTree;
    private JTextField referenceField;

    public RevertToGitReferenceDialog(Project project, List<String> filePaths) {
        super(project);
        this.project = project;
        this.filePaths = filePaths;
        setTitle("Revert Files to Git Reference");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(600, 400));

        // Reference input
        JPanel refPanel = new JPanel(new BorderLayout());
        refPanel.add(new JLabel("Git Reference:"), BorderLayout.WEST);
        referenceField = new JTextField("HEAD");
        refPanel.add(referenceField, BorderLayout.CENTER);
        panel.add(refPanel, BorderLayout.NORTH);

        // Prepare Change objects for display
        List<Change> changes = new ArrayList<>();
        for (String path : filePaths) {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
            if (vf != null) {
                changes.add(new Change(null, new SimpleContentRevision("", vf.toNioPath(), "")));
            }
        }

        // Create a custom ChangesTreeImpl that implements buildTreeModel
        changesTree = new ChangesTreeImpl<Change>(project, false, true, null) {
            @Override
            public DefaultMutableTreeNode buildTreeModel(java.util.List<? extends Change> changes) {
                DefaultMutableTreeNode root = new DefaultMutableTreeNode();
                // Group by directory
                Map<String, java.util.List<Change>> byDir = new TreeMap<>();
                for (Change change : changes) {
                    FilePath filePath = change.getAfterRevision() != null ?
                            change.getAfterRevision().getFile() :
                            change.getBeforeRevision().getFile();
                    String pathStr = filePath.getPath();
                    int idx = pathStr.lastIndexOf('/');
                    String dir = idx >= 0 ? pathStr.substring(0, idx) : "";
                    byDir.computeIfAbsent(dir, k -> new ArrayList<>()).add(change);
                }
                for (Map.Entry<String, java.util.List<Change>> entry : byDir.entrySet()) {
                    DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(entry.getKey());
                    for (Change ch : entry.getValue()) {
                        dirNode.add(new DefaultMutableTreeNode(ch));
                    }
                    root.add(dirNode);
                }
                return root;
            }

            // Optional: Show file name for each node
            @Override
            protected String getPathPresentation(Object userObject) {
                if (userObject instanceof Change) {
                    Change change = (Change) userObject;
                    FilePath filePath = change.getAfterRevision() != null ?
                            change.getAfterRevision().getFile() :
                            change.getBeforeRevision().getFile();
                    String path = filePath.getPath();
                    int idx = path.lastIndexOf('/');
                    return idx >= 0 ? path.substring(idx + 1) : path;
                }
                return super.getPathPresentation(userObject);
            }
        };

        // Use reflection to set the changes since rebuildTree might not be available
        try {
            java.lang.reflect.Method method = changesTree.getClass().getMethod("setChangesToDisplay", java.util.List.class);
            method.invoke(changesTree, changes);
        } catch (Exception e) {
            // Fallback: try to use another method or set directly
        }

        panel.add(new JScrollPane(changesTree), BorderLayout.CENTER);

        return panel;
    }

    @Override
    protected void doOKAction() {
        String ref = referenceField.getText().trim();

        // Get selected changes using reflection since getIncludedChanges might not be available
        Collection<Change> selectedChanges = null;
        try {
            java.lang.reflect.Method method = changesTree.getClass().getMethod("getSelectedChanges");
            selectedChanges = (Collection<Change>) method.invoke(changesTree);
        } catch (Exception e) {
            // Fallback to all changes if we can't get selected ones
            selectedChanges = new ArrayList<>();
            for (String path : filePaths) {
                VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
                if (vf != null) {
                    selectedChanges.add(new Change(null, new SimpleContentRevision("", vf.toNioPath(), "")));
                }
            }
        }

        if (ref.isEmpty() || selectedChanges.isEmpty()) {
            super.doOKAction();
            return;
        }

        List<String> filesToRevert = selectedChanges.stream()
                .map(ch -> {
                    FilePath filePath = ch.getAfterRevision() != null ?
                            ch.getAfterRevision().getFile() :
                            ch.getBeforeRevision().getFile();
                    return filePath.getPath();
                })
                .collect(Collectors.toList());

        // Run Git operations in a background task (off EDT)
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Reverting Files to " + ref, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
                Git git = Git.getInstance();
                Map<GitRepository, List<String>> filesByRepo = new HashMap<>();

                for (String file : filesToRevert) {
                    VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(file);
                    if (vf != null) {
                        GitRepository repo = repoManager.getRepositoryForFileQuick(vf);
                        if (repo != null) {
                            filesByRepo.computeIfAbsent(repo, r -> new ArrayList<>()).add(file);
                        }
                    }
                }

                for (Map.Entry<GitRepository, List<String>> entry : filesByRepo.entrySet()) {
                    GitRepository repo = entry.getKey();
                    List<String> files = entry.getValue();
                    GitLineHandler handler = new GitLineHandler(project, repo.getRoot(), GitCommand.CHECKOUT);
                    handler.addParameters(ref);
                    handler.addParameters("--");
                    handler.endOptions();
                    handler.addParameters(files);
                    git.runCommand(handler);

                    // Refresh files after checkout
                    ApplicationManager.getApplication().invokeLater(() -> {
                        for (String f : files) {
                            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(f);
                            if (vf != null) vf.refresh(true, false);
                        }
                    });
                }
            }
        });

        super.doOKAction();
    }
}