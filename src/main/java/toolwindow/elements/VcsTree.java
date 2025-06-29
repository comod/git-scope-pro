package toolwindow.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import implementation.compare.ChangesService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

    public VcsTree(Project project) {
        this.project = project;
        this.setLayout(new BorderLayout());
        this.createElement();
        this.addListener();
    }

    public void createElement() {
        JLabel initialLabel = new JLabel("No changes to display");
        initialLabel.setHorizontalAlignment(SwingConstants.CENTER);
        this.add(initialLabel, BorderLayout.CENTER);
    }

    public void addListener() {
        // Add any necessary listeners
    }

    private boolean shouldSkipUpdate(Collection<Change> newChanges) {
        // Handle null cases
        if (newChanges == null && lastChanges == null) {
            return true; // Both null, no update needed
        }
        if (newChanges == null || lastChanges == null) {
            return false; // One is null, other isn't - update needed
        }

        // Handle error state markers
        if (newChanges instanceof ChangesService.ErrorStateMarker ||
                lastChanges instanceof ChangesService.ErrorStateMarker) {
            // Always update error states to be safe
            return false;
        }

        // Quick size check
        if (newChanges.size() != lastChanges.size()) {
            return false;
        }

        // Quick hash check
        int newHashCode = calculateChangesHashCode(newChanges);
        if (newHashCode != lastChangesHashCode) {
            return false;
        }

        // If we have a browser already showing the same content, skip
        if (currentBrowser != null && !newChanges.isEmpty()) {
            LOG.debug("Skipping update - content appears unchanged");
            return true;
        }

        return false;
    }

    private int calculateChangesHashCode(Collection<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }

        // Create a hash based on the change contents
        int hash = 17;
        for (Change change : changes) {
            if (change != null) {
                hash = hash * 31 + Objects.hashCode(change.getBeforeRevision());
                hash = hash * 31 + Objects.hashCode(change.getAfterRevision());
            }
        }
        return hash;
    }

    public void update(Collection<Change> changes) {
        if (project.isDisposed()) {
            LOG.debug("Project is disposed, ignoring update");
            return;
        }

        // Check if we can skip this update
        if (shouldSkipUpdate(changes)) {
            LOG.debug("Skipping unnecessary update");
            return;
        }

        // Update tracking state
        lastChanges = changes != null ? new ArrayList<>(changes) : null;
        lastChangesHashCode = calculateChangesHashCode(changes);

        // Generate new sequence number for this update
        final long sequenceNumber = updateSequence.incrementAndGet();
        LOG.debug("Starting update sequence " + sequenceNumber);

        // Cancel any previous update
        CompletableFuture<Void> previousUpdate = currentUpdate.get();
        if (previousUpdate != null && !previousUpdate.isDone()) {
            LOG.debug("Cancelling previous update");
            previousUpdate.cancel(true);
        }

        // Handle immediate cases (null, empty, or error states)
        if (changes == null || changes.isEmpty() || changes instanceof ChangesService.ErrorStateMarker) {
            JLabel statusLabel = createStatusLabel(changes);
            SwingUtilities.invokeLater(() -> setComponentIfCurrent(statusLabel, sequenceNumber));
            currentBrowser = null;
            return;
        }

        // Create the async update chain - no loading indicator, direct processing
        CompletableFuture<Void> updateFuture = CompletableFuture
                .supplyAsync(() -> {
                    // Check if still current before processing
                    if (!isCurrentSequence(sequenceNumber)) {
                        throw new CompletionException(new InterruptedException("Update cancelled - sequence outdated"));
                    }

                    LOG.debug("Processing changes for sequence " + sequenceNumber);
                    // Make defensive copy to avoid threading issues
                    return new ArrayList<>(changes);
                }, AppExecutorUtil.getAppExecutorService())

                .thenCompose(changesCopy -> {
                    // Check again before creating browser
                    if (!isCurrentSequence(sequenceNumber)) {
                        throw new CompletionException(new InterruptedException("Update cancelled - sequence outdated"));
                    }

                    LOG.debug("Creating browser for sequence " + sequenceNumber);
                    return MySimpleChangesBrowser.createAsync(project, changesCopy);
                })

                .thenAccept(browser -> {
                    // Final UI update on EDT
                    SwingUtilities.invokeLater(() -> {
                        if (isCurrentSequence(sequenceNumber) && !project.isDisposed()) {
                            LOG.debug("Setting browser component for sequence " + sequenceNumber);
                            setComponentSafely(browser);
                            currentBrowser = browser;
                        } else {
                            LOG.debug("Ignoring browser update for outdated sequence " + sequenceNumber);
                        }
                    });
                })

                .exceptionally(throwable -> {
                    LOG.debug("Update failed for sequence " + sequenceNumber, throwable);

                    // Only show error if this is still the current sequence
                    if (isCurrentSequence(sequenceNumber)) {
                        SwingUtilities.invokeLater(() -> {
                            if (!project.isDisposed() && isCurrentSequence(sequenceNumber)) {
                                JLabel errorLabel = createErrorLabel(throwable);
                                setComponentSafely(errorLabel);
                                currentBrowser = null; // Clear current browser on error
                            }
                        });
                    }
                    return null;
                })

                // Add timeout protection
                .completeOnTimeout(null, UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Store the current update
        currentUpdate.set(updateFuture);
    }

    private boolean isCurrentSequence(long sequenceNumber) {
        return sequenceNumber == updateSequence.get();
    }

    private JLabel createStatusLabel(Collection<Change> changes) {
        if (changes == null) {
            return new JBLabel("Collecting changes...", AllIcons.Process.Step_1, JLabel.CENTER);
        } else if (changes instanceof ChangesService.ErrorStateMarker) {
            return new JLabel("Invalid git scope", AllIcons.General.Error, JLabel.CENTER);
        } else {
            return new JLabel("No changes to display", AllIcons.General.Information, JLabel.CENTER);
        }
    }

    private JLabel createErrorLabel(Throwable throwable) {
        String message = throwable.getCause() instanceof InterruptedException ?
                "Update cancelled" :
                "Error loading changes: " + throwable.getMessage();
        return new JLabel(message, AllIcons.General.Error, JLabel.CENTER);
    }

    private void setComponentIfCurrent(Component component, long sequenceNumber) {
        if (isCurrentSequence(sequenceNumber)) {
            setComponentSafely(component);
            if (!(component instanceof MySimpleChangesBrowser)) {
                currentBrowser = null; // Clear current browser if setting a different component
            }
        }
    }

    private void setComponentSafely(Component component) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setComponentSafely(component));
            return;
        }

        if (project.isDisposed()) {
            return;
        }

        try {
            // Save scroll position BEFORE replacing component
            ScrollPosition savedPosition = saveScrollPosition();

            this.removeAll();
            this.add(component, BorderLayout.CENTER);
            this.revalidate();
            this.repaint();

            // Restore scroll position IMMEDIATELY after layout
            restoreScrollPosition(component, savedPosition);

            LOG.debug("Component updated successfully with position restored");
        } catch (Exception e) {
            LOG.warn("Error updating component", e);
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
                JScrollBar vScrollBar = scrollPane.getVerticalScrollBar();
                JScrollBar hScrollBar = scrollPane.getHorizontalScrollBar();

                int vPos = vScrollBar.getValue();
                int hPos = hScrollBar.getValue();

                LOG.debug("Saved scroll position: vertical=" + vPos + ", horizontal=" + hPos);
                return new ScrollPosition(vPos, hPos, true);
            }
        } catch (Exception e) {
            LOG.debug("Failed to save scroll position", e);
        }
        return ScrollPosition.invalid();
    }

    private void restoreScrollPosition(Component newComponent, ScrollPosition position) {
        if (!position.isValid) {
            return;
        }

        // Use invokeLater to ensure the component is fully laid out first
        SwingUtilities.invokeLater(() -> {
            try {
                JScrollPane scrollPane = findScrollPane(this);
                if (scrollPane != null) {
                    JScrollBar vScrollBar = scrollPane.getVerticalScrollBar();
                    JScrollBar hScrollBar = scrollPane.getHorizontalScrollBar();

                    // Restore positions
                    if (position.verticalValue <= vScrollBar.getMaximum()) {
                        vScrollBar.setValue(position.verticalValue);
                    }
                    if (position.horizontalValue <= hScrollBar.getMaximum()) {
                        hScrollBar.setValue(position.horizontalValue);
                    }

                    LOG.debug("Restored scroll position: vertical=" + position.verticalValue +
                            ", horizontal=" + position.horizontalValue);
                }
            } catch (Exception e) {
                LOG.debug("Failed to restore scroll position", e);
            }
        });
    }

    private JScrollPane findScrollPane(Component component) {
        // Look up the component hierarchy to find the scroll pane
        Component current = component;
        while (current != null) {
            if (current instanceof JScrollPane) {
                return (JScrollPane) current;
            }
            current = current.getParent();
        }

        // If not found in parents, look for scroll pane in children
        // This handles cases where the scroll pane contains our component
        return findScrollPaneInChildren(component);
    }

    private JScrollPane findScrollPaneInChildren(Component component) {
        if (component instanceof JScrollPane) {
            return (JScrollPane) component;
        }

        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                if (child instanceof JScrollPane) {
                    return (JScrollPane) child;
                }
                // Recursively search in children
                JScrollPane found = findScrollPaneInChildren(child);
                if (found != null) {
                    return found;
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
        // Clear saved state
        currentBrowser = null;
        lastChanges = null;
        lastChangesHashCode = 0;
    }
}