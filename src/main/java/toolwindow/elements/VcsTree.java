package toolwindow.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import implementation.compare.ChangesService;
import service.ViewService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class VcsTree extends JPanel {
    private static final Logger LOG = Logger.getInstance(VcsTree.class);
    private static final int UPDATE_TIMEOUT_SECONDS = 30;

    private final Project project;

    // Simple sequence-based approach - much more robust
    private final AtomicLong updateSequence = new AtomicLong(0);
    private final AtomicReference<CompletableFuture<Void>> currentUpdate = new AtomicReference<>();

    // Track current state to avoid unnecessary updates
    private MySimpleChangesBrowser currentBrowser;
    private Collection<Change> lastChanges;
    private int lastChangesHashCode = 0;

    // Per-tab state tracking
    private final Map<String, Collection<Change>> lastChangesPerTab = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastChangesHashCodePerTab = new ConcurrentHashMap<>();

    // Per-tab scroll position tracking
    private final Map<String, ScrollPosition> scrollPositionPerTab = new ConcurrentHashMap<>();

    public VcsTree(Project project) {
        this.project = project;
        this.setLayout(new BorderLayout());
        this.createElement();
        this.addListener();
    }

    // Get current tab identifier
    private String getCurrentTabId() {
        try {
            ViewService viewService = project.getService(ViewService.class);
            if (viewService != null) {
                int tabIndex = viewService.getTabIndex();
                return "tab_" + tabIndex;
            }
        } catch (Exception e) {
            LOG.warn("Failed to get current tab ID", e);
        }
        return "default_tab";
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

        // Use per-tab values if available, otherwise fall back to global
        Collection<Change> effectiveLastChanges = tabLastChanges != null ? tabLastChanges : lastChanges;
        int effectiveLastHashCode = tabLastChangesHashCode != null ? tabLastChangesHashCode : lastChangesHashCode;

        // Handle null cases
        if (newChanges == null && effectiveLastChanges == null) {
            return true; // Both null, no update needed
        }
        if (newChanges == null || effectiveLastChanges == null) {
            return false; // One is null, other isn't - update needed
        }

        // Handle error state markers
        if (newChanges instanceof ChangesService.ErrorStateMarker ||
                effectiveLastChanges instanceof ChangesService.ErrorStateMarker) {
            return false; // Always update error states
        }

        // Quick size check
        if (newChanges.size() != effectiveLastChanges.size()) {
            return false;
        }

        // Hash check - if they're the same, skip update
        int newHashCode = calculateChangesHashCode(newChanges);
        if (newHashCode == effectiveLastHashCode) {
            return true; // Changes are identical - skip update
        }

        return false; // Changes are different - update needed
    }

    private int calculateChangesHashCode(Collection<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }

        // Extract and sort file paths - this gives us a stable, order-independent hash
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

        // Check if we can skip this update
        if (shouldSkipUpdate(changes)) {
            return;
        }

        // Update tracking state for current tab and keep global for compatibility
        String currentTabId = getCurrentTabId();
        lastChangesPerTab.put(currentTabId, changes != null ? new ArrayList<>(changes) : null);
        lastChangesHashCodePerTab.put(currentTabId, calculateChangesHashCode(changes));
        lastChanges = changes;
        lastChangesHashCode = calculateChangesHashCode(changes);

        // Generate new sequence number for this update
        final long sequenceNumber = updateSequence.incrementAndGet();

        // Cancel any previous update
        CompletableFuture<Void> previousUpdate = currentUpdate.get();
        if (previousUpdate != null && !previousUpdate.isDone()) {
            previousUpdate.cancel(true);
        }

        // Handle immediate cases (null, empty, or error states)
        if (changes == null || changes.isEmpty() || changes instanceof ChangesService.ErrorStateMarker) {
            JLabel statusLabel = createStatusLabel(changes);
            SwingUtilities.invokeLater(() -> setComponentIfCurrent(statusLabel, sequenceNumber));
            currentBrowser = null;
            return;
        }

        // Create the async update chain
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
                    return MySimpleChangesBrowser.createAsync(project, changesCopy);
                })

                .thenAccept(browser -> {
                    SwingUtilities.invokeLater(() -> {
                        if (isCurrentSequence(sequenceNumber) && !project.isDisposed()) {
                            setComponent(browser);
                            currentBrowser = browser;
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
        // Assume savedPosition and currentTabId are already defined at your scope.
        ScrollPosition savedPosition = scrollPositionPerTab.get(getCurrentTabId());
        if (savedPosition != null) {
            if (SwingUtilities.isEventDispatchThread()) {
                restoreScrollPosition(this, savedPosition);
            } else {
                SwingUtilities.invokeLater(() -> restoreScrollPosition(this, savedPosition));
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

            // Save current scroll position for the current tab
            ScrollPosition currentPosition = saveScrollPosition();
            if (currentPosition.isValid) {
                scrollPositionPerTab.put(currentTabId, currentPosition);
            }

            // Remove all components and add the new one
            this.removeAll();
            this.add(component, BorderLayout.CENTER);
            this.revalidate();
            this.repaint();
        } catch (Exception e) {
            LOG.warn("Error updating VcsTree component", e);
            try {
                this.removeAll();
                this.add(component, BorderLayout.CENTER);
                this.revalidate();
                this.repaint();
            } catch (Exception fallbackError) {
                LOG.error("Fallback component update also failed", fallbackError);
            }
        }
    }

    // Simple data class to hold scroll position
    private static class ScrollPosition {
        final int verticalValue;
        final int horizontalValue;
        final boolean isValid;

        ScrollPosition(int vertical, int horizontal, boolean valid) {
            this.verticalValue = vertical;
            this.horizontalValue = horizontal;
            this.isValid = valid;
        }

        static ScrollPosition invalid() {
            return new ScrollPosition(0, 0, false);
        }
    }

    private ScrollPosition saveScrollPosition() {
        try {
            JScrollPane scrollPane = findScrollPane(this);
            if (scrollPane != null) {
                JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
                JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

                // Check that both scrollbars are not null before setting valid to true
                boolean valid = verticalScrollBar != null && horizontalScrollBar != null;
                return new ScrollPosition(
                        verticalScrollBar != null ? verticalScrollBar.getValue() : 0,
                        horizontalScrollBar != null ? horizontalScrollBar.getValue() : 0,
                        valid
                );

            }
        } catch (Exception e) {
            LOG.debug("Could not save scroll position", e);
        }
        return ScrollPosition.invalid();
    }

    private void restoreScrollPosition(Component newComponent, ScrollPosition position) {
        if (!position.isValid) {
            return;
        }

        JScrollPane scrollPane = findScrollPane(newComponent);
        if (scrollPane != null) {
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

            if (verticalScrollBar != null) {
                verticalScrollBar.setValue(position.verticalValue);
            }
            if (horizontalScrollBar != null) {
                horizontalScrollBar.setValue(position.horizontalValue);
            }
        }
    }

    private JScrollPane findScrollPane(Component component) {
        if (component instanceof JScrollPane) {
            return (JScrollPane) component;
        }
        return findScrollPaneInChildren(component);
    }

    private JScrollPane findScrollPaneInChildren(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                JScrollPane result = findScrollPane(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    // Clean up when component is disposed
    @Override
    public void removeNotify() {
        super.removeNotify();
        CompletableFuture<Void> current = currentUpdate.get();
        if (current != null && !current.isDone()) {
            current.cancel(true);
        }
        lastChangesPerTab.clear();
        lastChangesHashCodePerTab.clear();
        scrollPositionPerTab.clear();
        currentBrowser = null;
    }
}