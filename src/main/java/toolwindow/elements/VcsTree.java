package toolwindow.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import implementation.compare.ChangesService;
import service.ViewService;
import state.WindowPositionTracker;
import state.WindowPositionTracker.ScrollPosition;
import system.Defs;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class VcsTree extends JPanel {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(VcsTree.class);
    private static final int UPDATE_TIMEOUT_SECONDS = 30;

    private final Project project;
    private final WindowPositionTracker positionTracker;

    private final AtomicLong updateSequence = new AtomicLong(0);
    private final AtomicReference<CompletableFuture<Void>> currentUpdate = new AtomicReference<>();

    private MySimpleChangesBrowser currentBrowser;
    private Collection<Change> lastChanges;
    private int lastChangesHashCode = 0;

    private final Map<String, Collection<Change>> lastChangesPerTab = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastChangesHashCodePerTab = new ConcurrentHashMap<>();

    // Use a single browser instance for this VcsTree to avoid toolbar recreation issues
    // When switching tabs, we just update the browser's contents instead of swapping components
    private MySimpleChangesBrowser singleBrowser = null;
    private CompletableFuture<MySimpleChangesBrowser> pendingBrowserCreation = null;

    public VcsTree(Project project) {
        this.project = project;
        this.positionTracker = new WindowPositionTracker(
                this::getCurrentTabId,
                this::onVcsTreeLoaded,
                () -> this
        );
        this.setLayout(new BorderLayout());
        this.createElement();
        this.addListener();
    }

    private void onVcsTreeLoaded(String tabId) {
        // This is called when VCS tree has finished loading
    }

    public void onTabSwitched() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Get the current tab ID to restore its scroll position
                String currentTabId = getCurrentTabId();

                // With single browser approach, the browser is already in place if it exists
                // We just need to restore the scroll position for this tab

                Component currentComponent = getComponentCount() > 0 ? getComponent(0) : null;
                if (currentComponent != null) {
                    positionTracker.attachScrollListeners(currentComponent);

                    SwingUtilities.invokeLater(() -> {
                        ScrollPosition savedPosition = positionTracker.getSavedScrollPosition(currentTabId);
                        if (savedPosition != null) {
                            positionTracker.restoreScrollPosition(currentComponent, savedPosition);
                        }
                        positionTracker.setScrollPositionRestored(true);
                    });
                } else {
                    positionTracker.setScrollPositionRestored(true);
                }
            } catch (Exception e) {
                LOG.error("Error re-attaching scroll listeners after tab switch", e);
                positionTracker.setScrollPositionRestored(true);
            }
        });
    }

    public void selectFile(VirtualFile file) {
        if (currentBrowser == null) { return; }
        List<Change> changes = currentBrowser.getAllChanges();

        Change targetChange = null;
        for (Change change : changes) {
            FilePath afterPath = ChangesUtil.getAfterPath(change);
            FilePath beforePath = ChangesUtil.getBeforePath(change);
            if ((afterPath != null && file.equals(afterPath.getVirtualFile())) ||
                    (beforePath != null && file.equals(beforePath.getVirtualFile()))) {
                targetChange = change;
                break;
            }
        }

        if (targetChange != null) {
            currentBrowser.selectEntries(Collections.singletonList(targetChange));
        }
    }

    private String getCurrentTabId() {
        try {
            ViewService viewService = project.getService(ViewService.class);
            if (viewService != null) {
                int tabIndex = viewService.getTabIndex();
                // Include project name to avoid conflicts when cache is static across projects
                return project.getName() + "_tab_" + tabIndex;
            }
        } catch (Exception e) {
            LOG.warn("Failed to get current tab ID", e);
        }
        return project.getName() + "_default_tab";
    }

    public void createElement() {
        JLabel initialLabel = new JLabel("No changes to display", AllIcons.General.Information, JLabel.CENTER);
        initialLabel.setHorizontalAlignment(SwingConstants.CENTER);
        this.add(initialLabel, BorderLayout.CENTER);
    }

    public void addListener() {
        // Add any necessary listeners
    }

    private boolean shouldSkipUpdate(Collection<Change> newChanges) {
        String currentTabId = getCurrentTabId();
        Collection<Change> tabLastChanges = lastChangesPerTab.get(currentTabId);
        Integer tabLastChangesHashCode = lastChangesHashCodePerTab.get(currentTabId);

        Collection<Change> effectiveLastChanges = tabLastChanges != null ? tabLastChanges : lastChanges;
        int effectiveLastHashCode = tabLastChangesHashCode != null ? tabLastChangesHashCode : lastChangesHashCode;

        if (newChanges == null && effectiveLastChanges == null) {
            return true;
        }
        if (newChanges == null || effectiveLastChanges == null) {
            return false;
        }

        if (newChanges instanceof ChangesService.ErrorStateMarker ||
                effectiveLastChanges instanceof ChangesService.ErrorStateMarker) {
            return false;
        }

        if (newChanges.size() != effectiveLastChanges.size()) {
            return false;
        }

        int newHashCode = calculateChangesHashCode(newChanges);
        return newHashCode == effectiveLastHashCode;
    }

    private int calculateChangesHashCode(Collection<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }

        java.util.List<String> filePaths = changes.stream()
                .filter(Objects::nonNull)
                .map(this::getChangePath)
                .filter(path -> !path.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        return Objects.hash(filePaths);
    }

    private String getChangePath(Change change) {
        ContentRevision revision = change.getAfterRevision() != null ?
                change.getAfterRevision() : change.getBeforeRevision();
        return revision != null ? revision.getFile().getPath() : "";
    }

    public void update(Collection<Change> changes) {
        if (project.isDisposed()) {
            return;
        }

        if (shouldSkipUpdate(changes)) {
            return;
        }

        final String tabId = getCurrentTabId();
        lastChangesPerTab.put(tabId, changes != null ? new ArrayList<>(changes) : null);
        lastChangesHashCodePerTab.put(tabId, calculateChangesHashCode(changes));
        lastChanges = changes;
        lastChangesHashCode = calculateChangesHashCode(changes);

        final long sequenceNumber = updateSequence.incrementAndGet();

        CompletableFuture<Void> previousUpdate = currentUpdate.get();
        if (previousUpdate != null && !previousUpdate.isDone()) {
            previousUpdate.cancel(true);
        }

        if (changes == null || changes.isEmpty() || changes instanceof ChangesService.ErrorStateMarker) {
            JLabel statusLabel = createStatusLabel(changes);
            SwingUtilities.invokeLater(() -> setComponentIfCurrent(statusLabel, sequenceNumber));
            //currentBrowser = null;
            return;
        }

        CompletableFuture<Void> updateFuture = CompletableFuture
                .supplyAsync(() -> {
                    if (!isCurrentSequence(sequenceNumber)) {
                        throw new CompletionException(new InterruptedException("Update cancelled - sequence outdated"));
                    }
                    return new ArrayList<>(changes);
                }, AppExecutorUtil.getAppExecutorService())

                .thenCompose(changesCopy -> {
                    if (!isCurrentSequence(sequenceNumber)) {
                        throw new CompletionException(new InterruptedException("Update cancelled - sequence outdated"));
                    }

                    // Reuse single browser instance if it exists
                    if (singleBrowser != null) {
                        LOG.debug("VcsTree: Reusing single browser instance");
                        return CompletableFuture.supplyAsync(() -> {
                            SwingUtilities.invokeLater(() -> {
                                if (!project.isDisposed()) {
                                    singleBrowser.setChangesToDisplay(changesCopy);
                                }
                            });
                            return singleBrowser;
                        });
                    }

                    // Check if browser is already being created
                    if (pendingBrowserCreation != null && !pendingBrowserCreation.isDone()) {
                        LOG.debug("VcsTree: Waiting for pending browser creation");
                        // Wait for the pending creation to complete
                        return pendingBrowserCreation.thenApply(browser -> {
                            // Update with new changes
                            SwingUtilities.invokeLater(() -> {
                                if (!project.isDisposed()) {
                                    browser.setChangesToDisplay(changesCopy);
                                }
                            });
                            return browser;
                        });
                    }

                    // Create single browser instance
                    LOG.debug("VcsTree: Creating single browser instance");
                    CompletableFuture<MySimpleChangesBrowser> creationFuture =
                            MySimpleChangesBrowser.createAsync(project, this, changesCopy)
                                    .thenApply(newBrowser -> {
                                        singleBrowser = newBrowser;
                                        pendingBrowserCreation = null;
                                        LOG.debug("VcsTree: Single browser created and cached");
                                        return newBrowser;
                                    });

                    pendingBrowserCreation = creationFuture;
                    return creationFuture;
                })

                .thenAccept(browser -> {
                    SwingUtilities.invokeLater(() -> {
                        if (isCurrentSequence(sequenceNumber) && !project.isDisposed()) {
                            // Set component if it's different from current
                            if (currentBrowser != browser) {
                                setComponent(browser);
                                currentBrowser = browser;
                            }
                        }
                    });
                })

                .exceptionally(throwable -> {
                    if (isCurrentSequence(sequenceNumber)) {
                        SwingUtilities.invokeLater(() -> {
                            if (!project.isDisposed() && isCurrentSequence(sequenceNumber)) {
                                JLabel errorLabel = createErrorLabel(throwable);
                                setComponent(errorLabel);
                                currentBrowser = null;
                                // Clear the single browser since we're showing an error
                                singleBrowser = null;
                            }
                        });
                    }
                    return null;
                })

                .completeOnTimeout(null, UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        currentUpdate.set(updateFuture);
    }

    private boolean isCurrentSequence(long sequenceNumber) {
        return updateSequence.get() == sequenceNumber;
    }

    private JLabel createStatusLabel(Collection<Change> changes) {
        if (changes == null) {
            return new JBLabel("Collecting changes...", AllIcons.Process.Step_1, JLabel.CENTER);
        } else if (changes instanceof ChangesService.ErrorStateMarker) {
            return new JBLabel("Invalid git scope", AllIcons.General.Error, JLabel.CENTER);
        } else {
            return new JBLabel("No changes to display", AllIcons.General.Information, JLabel.CENTER);
        }
    }

    private JLabel createErrorLabel(Throwable throwable) {
        String message = throwable.getMessage();
        if (throwable instanceof InterruptedException ||
                (throwable instanceof CompletionException && throwable.getCause() instanceof InterruptedException)) {
            return new JBLabel("No changes to display", AllIcons.General.Information, JLabel.CENTER);
        }
        return new JBLabel("Error: " + (message != null ? message : "Unknown error"),
                AllIcons.General.Error, JLabel.CENTER);
    }

    private void setComponentIfCurrent(Component component, long sequenceNumber) {
        if (isCurrentSequence(sequenceNumber)) {
            setComponent(component);
        }
    }

    public void performScrollRestoration() {
        ScrollPosition savedPosition = positionTracker.getSavedScrollPosition(getCurrentTabId());
        if (savedPosition != null) {
            if (SwingUtilities.isEventDispatchThread()) {
                positionTracker.restoreScrollPosition(this, savedPosition);
            } else {
                SwingUtilities.invokeLater(() -> positionTracker.restoreScrollPosition(this, savedPosition));
            }
        }
    }

    private void setComponent(Component component) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setComponent(component));
            return;
        }

        if (project.isDisposed()) {
            return;
        }

        try {
            String currentTabId = getCurrentTabId();

            if (positionTracker.isScrollPositionRestored()) {
                ScrollPosition currentPosition = positionTracker.saveScrollPosition();
                if (currentPosition.isValid) {
                    positionTracker.setSavedScrollPosition(currentTabId, currentPosition);
                }
            }

            positionTracker.setScrollPositionRestored(false);

            // Only remove/add if the component actually changed to avoid triggering toolbar updates
            Component currentComponent = getComponentCount() > 0 ? getComponent(0) : null;
            if (currentComponent != component) {
                this.removeAll();
                this.add(component, BorderLayout.CENTER);
                this.revalidate();
                this.repaint();
            }

            SwingUtilities.invokeLater(() -> {
                positionTracker.attachScrollListeners(component);

                SwingUtilities.invokeLater(() -> {
                    ScrollPosition savedPosition = positionTracker.getSavedScrollPosition(currentTabId);
                    if (savedPosition != null) {
                        positionTracker.restoreScrollPosition(component, savedPosition);
                    }
                    positionTracker.setScrollPositionRestored(true);
                });
            });

        } catch (Exception e) {
            LOG.error("Error updating VcsTree component", e);
            try {
                this.removeAll();
                this.add(component, BorderLayout.CENTER);
                this.revalidate();
                this.repaint();
                positionTracker.setScrollPositionRestored(true);
            } catch (Exception fallbackError) {
                LOG.error("Fallback component update also failed", fallbackError);
            }
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();

        positionTracker.cleanup();

        CompletableFuture<Void> current = currentUpdate.get();
        if (current != null && !current.isDone()) {
            current.cancel(true);
        }
        lastChangesPerTab.clear();
        lastChangesHashCodePerTab.clear();

        // Clear the single browser instance for this VcsTree
        singleBrowser = null;
        pendingBrowserCreation = null;
    }
}