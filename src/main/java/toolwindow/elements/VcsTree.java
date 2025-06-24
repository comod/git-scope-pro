
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.AsyncProcessIcon;
import implementation.compare.ChangesService;

import static implementation.compare.ChangesService.ERROR_STATE;

public class VcsTree extends JPanel {
    private static final Logger LOG = Logger.getInstance(VcsTree.class);
    private static final int BROWSER_CREATION_TIMEOUT_SECONDS = 30;

    private final Project project;
    private final AtomicBoolean isUpdateInProgress = new AtomicBoolean(false);
    private CompletableFuture<Void> currentUpdateFuture;
    private ScheduledExecutorService timeoutScheduler;

    // Track the sequence number of updates to prevent out-of-order updates
    private final AtomicLong currentUpdateSequence = new AtomicLong(0);

    // Keep track of what component is currently showing
    private final AtomicReference<String> currentlyShowingComponent = new AtomicReference<>("none");

    // Keep track of component state flags per sequence
    private final ConcurrentHashMap<Long, String> sequenceComponentState = new ConcurrentHashMap<>();

    public VcsTree(Project project) {
        this.project = project;
        this.setLayout(new BorderLayout());
        LOG.debug("VcsTree constructor called");
        this.createElement();
        this.addListener();
    }

    public void createElement() {
        // Initialize with empty or placeholder content
        JLabel initialLabel = new JLabel("No changes to display");
        initialLabel.setHorizontalAlignment(SwingConstants.CENTER);
        this.add(initialLabel, BorderLayout.CENTER);
        currentlyShowingComponent.set("initial");
    }

    public void addListener() {
        // Add any necessary listeners
    }

    private void setLoading(long sequenceNumber) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setLoading(sequenceNumber));
            return;
        }

        // Check if this sequence has already been processed further
        String existingState = sequenceComponentState.get(sequenceNumber);
        if (existingState != null && (existingState.equals("browser") || existingState.equals("completed"))) {
            LOG.debug("Skipping loading set for sequence " + sequenceNumber +
                    " because sequence already has a " + existingState + " component");
            return;
        }

        // Only set loading if this is the current sequence and we're not already showing loading
        if (sequenceNumber == currentUpdateSequence.get() &&
                !currentlyShowingComponent.get().equals("loading") &&
                !project.isDisposed()) {

            Component loadingComponent = centeredLoadingPanel(loadingIcon());
            this.removeAll();
            this.add(loadingComponent, BorderLayout.CENTER);
            this.revalidate();
            this.repaint();

            // Update both global and sequence-specific state
            currentlyShowingComponent.set("loading");
            sequenceComponentState.put(sequenceNumber, "loading");

            LOG.debug("Loading component set for sequence " + sequenceNumber);
        } else {
            LOG.debug("Skipping loading set for sequence " + sequenceNumber +
                    " (current sequence: " + currentUpdateSequence.get() +
                    ", current component: " + currentlyShowingComponent.get() + ")");
        }
    }

    public void update(Collection<Change> changes) {
        LOG.debug("VcsTree.update called with " + (changes == null ? "null" : changes.size()) + " changes");

        // Check if project is already disposed
        if (project.isDisposed()) {
            LOG.debug("Project is already disposed, not starting update");
            return;
        }

        // Cancel any previous update in progress
        cancelCurrentUpdate();

        // Generate a new sequence number for this update
        final long sequenceNumber = currentUpdateSequence.incrementAndGet();
        LOG.debug("Starting update sequence " + sequenceNumber);

        // Initialize sequence state
        sequenceComponentState.put(sequenceNumber, "started");

        // Use compareAndSet to ensure we don't have concurrent updates
        if (!isUpdateInProgress.compareAndSet(false, true)) {
            LOG.debug("Update already in progress, ignoring new update request");
            return; // Already updating
        }
        //System.out.println("Changes: " + changes);
        if (changes == null || changes.isEmpty() || changes instanceof ChangesService.ErrorStateMarker) {
            // Reset the flag and return early if there are no changes
            String emptyChangeReason;
            JLabel noChangesLabel;
            if (changes == null) {
                noChangesLabel =  new JBLabel("Collecting changes...", AllIcons.Process.Step_1, JLabel.LEFT);
            }  else if (changes instanceof ChangesService.ErrorStateMarker) {
                emptyChangeReason = "Invalid git scope";
                noChangesLabel = new JLabel(emptyChangeReason, AllIcons.General.Error, JLabel.LEFT);
            } else {
                emptyChangeReason = "No changes to display";
                noChangesLabel = new JLabel(emptyChangeReason, AllIcons.General.Information, JLabel.LEFT);
            }
            isUpdateInProgress.set(false);
            SwingUtilities.invokeLater(() -> {
                if (sequenceNumber == currentUpdateSequence.get() && !project.isDisposed()) {
                    noChangesLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    setComponentForSequence(noChangesLabel, "no-changes", sequenceNumber);
                }
            });
            return;
        }

        // Show loading indicator while we prepare the changes browser asynchronously
        SwingUtilities.invokeLater(() -> setLoading(sequenceNumber));
        LOG.debug("Starting async update of VcsTree for sequence " + sequenceNumber);

        // Setup a timeout guard to reset the flag if operation takes too long
        setupTimeoutGuard(changes, sequenceNumber);

        // Track operation time for diagnostics
        long startTime = System.currentTimeMillis();

        // Make a defensive copy of changes to avoid threading issues
        final Collection<Change> changesCopy = new ArrayList<>(changes);

        try {
            // Run everything in background thread to avoid EDT slowdowns
            currentUpdateFuture = CompletableFuture.supplyAsync(() -> {
                        LOG.debug("Preparing changes in background thread for sequence " + sequenceNumber);
                        // Check if we should continue
                        if (shouldAbortSequence(sequenceNumber)) {
                            throw new CompletionException(
                                    new InterruptedException("Update sequence cancelled"));
                        }
                        // Prepare everything off EDT
                        return changesCopy;
                    }, AppExecutorUtil.getAppExecutorService())
                    .thenCompose(processedChanges -> {
                        // Check if we should continue
                        if (shouldAbortSequence(sequenceNumber)) {
                            throw new CompletionException(
                                    new InterruptedException("Update sequence cancelled"));
                        }

                        LOG.debug("Processed changes, creating browser for sequence " + sequenceNumber);
                        // Update sequence state to prevent loading indicator from showing after this point
                        sequenceComponentState.put(sequenceNumber, "browser-pending");

                        // Create browser asynchronously using our improved method
                        return MySimpleChangesBrowser.createAsync(project, processedChanges);
                    })
                    .thenAccept(browser -> {
                        // This will run on EDT since the factory method ensures it
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        LOG.debug("Browser created successfully in " + elapsedTime + "ms for sequence " + sequenceNumber);

                        // Check if we should abort
                        if (shouldAbortSequence(sequenceNumber)) {
                            LOG.debug("Sequence " + sequenceNumber + " is no longer current, not updating UI");
                            return;
                        }

                        if (!project.isDisposed()) {
                            try {
                                // Update sequence state to browser
                                sequenceComponentState.put(sequenceNumber, "browser");

                                // Use our sequence-aware component setter
                                setComponentForSequence(browser, "browser", sequenceNumber);

                                // Mark as completed
                                sequenceComponentState.put(sequenceNumber, "completed");

                                LOG.debug("UI updated successfully for sequence " + sequenceNumber);
                            } catch (Exception ex) {
                                LOG.warn("Error setting browser component", ex);
                                JLabel errorLabel = new JLabel("Error loading changes: " + ex.getMessage());
                                sequenceComponentState.put(sequenceNumber, "error");
                                setComponentForSequence(errorLabel, "error", sequenceNumber);
                            }
                        } else {
                            LOG.debug("Project disposed, not updating UI");
                        }

                        // Cancel the timeout guard since we completed successfully
                        cancelTimeoutGuard();
                        isUpdateInProgress.set(false);
                    })
                    .exceptionally(ex -> {
                        // Handle exceptions during async creation
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        LOG.warn("Browser creation failed after " + elapsedTime + "ms for sequence " + sequenceNumber, ex);

                        // Check if this is still the current sequence
                        if (shouldAbortSequence(sequenceNumber)) {
                            LOG.debug("Sequence " + sequenceNumber + " is no longer current, not showing error UI");
                            return null;
                        }

                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        String errorMessage = "Error processing changes: " + cause.getMessage();
                        LOG.warn(errorMessage + " for sequence " + sequenceNumber);

                        // Try a simpler approach as fallback
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (!project.isDisposed() &&
                                    sequenceNumber == currentUpdateSequence.get()) {
                                try {
                                    // Create a simple fallback UI
                                    JPanel fallbackPanel = new JPanel(new BorderLayout());
                                    JLabel errorLabel = new JLabel("Error loading changes viewer: " +
                                            cause.getMessage());
                                    fallbackPanel.add(errorLabel, BorderLayout.NORTH);

                                    // Add a retry button
                                    JButton retryButton = new JButton("Retry");
                                    retryButton.addActionListener(e -> update(changesCopy));
                                    fallbackPanel.add(retryButton, BorderLayout.SOUTH);

                                    sequenceComponentState.put(sequenceNumber, "error");
                                    setComponentForSequence(fallbackPanel, "fallback", sequenceNumber);
                                } catch (Exception e) {
                                    // Last resort fallback
                                    LOG.error("Critical error creating fallback UI", e);
                                    sequenceComponentState.put(sequenceNumber, "critical-error");
                                    setComponentForSequence(
                                            new JLabel("Critical error in changes display"),
                                            "critical-error",
                                            sequenceNumber
                                    );
                                }
                            }

                            // Cancel the timeout guard since we completed with an error
                            cancelTimeoutGuard();
                            isUpdateInProgress.set(false);
                        });
                        return null;
                    });
        } catch (Exception e) {
            // Handle any unexpected exceptions during setup
            LOG.error("Unexpected error setting up async operations for sequence " + sequenceNumber, e);
            isUpdateInProgress.set(false);

            SwingUtilities.invokeLater(() -> {
                if (!project.isDisposed() && sequenceNumber == currentUpdateSequence.get()) {
                    JLabel errorLabel = new JLabel("Error setting up changes display: " + e.getMessage());
                    sequenceComponentState.put(sequenceNumber, "setup-error");
                    setComponentForSequence(errorLabel, "setup-error", sequenceNumber);
                }
            });
        }
    }

    private boolean shouldAbortSequence(long sequenceNumber) {
        // Check if this is still the current sequence
        if (sequenceNumber != currentUpdateSequence.get()) {
            LOG.debug("Sequence " + sequenceNumber + " is no longer current");
            return true;
        }

        // Check if this sequence was cancelled
        if (currentUpdateFuture != null && currentUpdateFuture.isCancelled()) {
            LOG.debug("Sequence " + sequenceNumber + " was cancelled");
            return true;
        }

        return false;
    }

    private void setComponentForSequence(Component component, String componentType, long sequenceNumber) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setComponentForSequence(component, componentType, sequenceNumber));
            return;
        }

        // Only set if this is still the current sequence
        if (sequenceNumber == currentUpdateSequence.get()) {
            // Check if component is valid
            if (component == null) {
                LOG.warn("Attempted to set null component for sequence " + sequenceNumber);
                return;
            }

            // Check sequence state to prevent loading being shown after browser
            String currentState = sequenceComponentState.get(sequenceNumber);
            if (componentType.equals("loading") &&
                    (currentState != null && (
                            currentState.equals("browser") ||
                                    currentState.equals("browser-pending") ||
                                    currentState.equals("completed")))) {
                LOG.debug("Skipping loading set because browser is already shown or pending");
                return;
            }

            LOG.debug("Setting " + componentType + " component for sequence " + sequenceNumber);

            // Remove all existing components and add the new one
            this.removeAll();
            this.add(component, BorderLayout.CENTER);

            // Force immediate layout update
            this.revalidate();
            this.repaint();

            // Update tracking
            currentlyShowingComponent.set(componentType);

            // Add a delayed validation for complex components like browser
            if (componentType.equals("browser")) {
                Timer timer = new Timer(100, e -> {
                    // Only do the extra validation if we're still showing this component
                    if (currentlyShowingComponent.get().equals(componentType)) {
                        revalidate();
                        repaint();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        } else {
            LOG.debug("Ignoring " + componentType + " component for outdated sequence " +
                    sequenceNumber + " (current: " + currentUpdateSequence.get() + ")");
        }
    }

    private void setupTimeoutGuard(Collection<Change> originalChanges, long sequenceNumber) {
        // Cancel any existing timeout guard
        cancelTimeoutGuard();

        // Create a new timeout guard
        timeoutScheduler = Executors.newScheduledThreadPool(1);
        timeoutScheduler.schedule(() -> {
            if (isUpdateInProgress.get() && sequenceNumber == currentUpdateSequence.get()) {
                LOG.warn("Update still in progress after " + BROWSER_CREATION_TIMEOUT_SECONDS +
                        " seconds for sequence " + sequenceNumber + " - forcing reset");

                // Cancel the current update
                cancelCurrentUpdate();

                // Reset the flag
                isUpdateInProgress.set(false);

                // Show error UI
                SwingUtilities.invokeLater(() -> {
                    if (!project.isDisposed() && sequenceNumber == currentUpdateSequence.get()) {
                        JPanel timeoutPanel = new JPanel(new BorderLayout());
                        JLabel timeoutLabel = new JLabel("Operation timed out after " +
                                BROWSER_CREATION_TIMEOUT_SECONDS + " seconds");
                        timeoutPanel.add(timeoutLabel, BorderLayout.NORTH);

                        // Add a retry button with the original changes
                        JButton retryButton = new JButton("Retry");
                        retryButton.addActionListener(e -> update(originalChanges));
                        timeoutPanel.add(retryButton, BorderLayout.SOUTH);

                        sequenceComponentState.put(sequenceNumber, "timeout");
                        setComponentForSequence(timeoutPanel, "timeout", sequenceNumber);
                    }
                });
            }
        }, BROWSER_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelTimeoutGuard() {
        if (timeoutScheduler != null && !timeoutScheduler.isShutdown()) {
            timeoutScheduler.shutdownNow();
            timeoutScheduler = null;
        }
    }

    private void cancelCurrentUpdate() {
        if (currentUpdateFuture != null && !currentUpdateFuture.isDone()) {
            LOG.debug("Cancelling current update");
            currentUpdateFuture.cancel(true);
        }

        // Also cancel the timeout guard
        cancelTimeoutGuard();
    }

    private JPanel centeredLoadingPanel(Component component) {
        JPanel masterPane = new JPanel(new GridBagLayout());
        masterPane.add(component);
        return masterPane;
    }

    private Component loadingIcon() {
        ClassLoader cldr = this.getClass().getClassLoader();
        java.net.URL imageURL = cldr.getResource("loading.png");
        if (imageURL != null) {
            ImageIcon imageIcon = new ImageIcon(imageURL);
            JLabel iconLabel = new JLabel();
            iconLabel.setIcon(imageIcon);
            imageIcon.setImageObserver(iconLabel);
            return iconLabel;
        } else {
            // Fallback if loading.png is not found
            JLabel loadingLabel = new JLabel("Loading...");
            loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
            return loadingLabel;
        }
    }

    // Handle visibility changes
    @Override
    public void addNotify() {
        super.addNotify();
        LOG.debug("VcsTree became visible, current component: " + currentlyShowingComponent.get());
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        LOG.debug("VcsTree became invisible");
    }

    // Clean up sequence states periodically to prevent memory leaks
    // This could be called on a timer or when the component is hidden
    private void cleanupOldSequenceStates() {
        long currentSequence = currentUpdateSequence.get();
        sequenceComponentState.keySet().removeIf(seq -> seq < (currentSequence - 10));
    }
}