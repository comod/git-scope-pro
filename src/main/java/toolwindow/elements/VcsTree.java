
package toolwindow.elements;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.intellij.icons.AllIcons;
import implementation.compare.ChangesService;

public class VcsTree extends JPanel {
    private static final Logger LOG = Logger.getInstance(VcsTree.class);
    private static final int UPDATE_TIMEOUT_SECONDS = 30;

    private final Project project;

    // Simple sequence-based approach - much more robust
    private final AtomicLong updateSequence = new AtomicLong(0);
    private final AtomicReference<CompletableFuture<Void>> currentUpdate = new AtomicReference<>();

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

    public void update(Collection<Change> changes) {
        if (project.isDisposed()) {
            LOG.debug("Project is disposed, ignoring update");
            return;
        }

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
            this.removeAll();
            this.add(component, BorderLayout.CENTER);
            this.revalidate();
            this.repaint();
            LOG.debug("Component updated successfully");
        } catch (Exception e) {
            LOG.warn("Error updating component", e);
        }
    }

    // Clean up when component is disposed
    @Override
    public void removeNotify() {
        super.removeNotify();
        CompletableFuture<Void> current = currentUpdate.get();
        if (current != null && !current.isDone()) {
            current.cancel(true);
        }
    }
}