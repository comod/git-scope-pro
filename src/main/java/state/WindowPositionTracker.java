package state;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WindowPositionTracker {
    private static final Logger LOG = Logger.getInstance(WindowPositionTracker.class);
    private static final int SCROLL_SAVE_DELAY_MS = 500;
    private static final long USER_ACTIVITY_TIMEOUT = 2000; // 2 seconds
    private static final int MAX_SCROLL_EVENTS_PER_TAB = 100; // Limit scroll event history size

    // Per-tab scroll position tracking
    private final Map<String, ScrollPosition> scrollPositionPerTab = new ConcurrentHashMap<>();

    // Per-tab scroll event history tracking (with size limit)
    private final Map<String, List<ScrollEvent>> scrollEventHistoryPerTab = new ConcurrentHashMap<>();

    // Current state (using WeakReference to prevent memory leaks)
    private WeakReference<JScrollPane> currentScrollPaneRef = new WeakReference<>(null);
    private Timer scrollSaveTimer;
    private Timer retryTimer; // Keep reference to retry timer for cleanup
    private boolean scrollPositionRestored = false;

    // Event counting for current session
    private final AtomicLong userScrollEvents = new AtomicLong(0);
    private final AtomicLong nonUserScrollEvents = new AtomicLong(0);

    // Callbacks
    private final Supplier<String> tabIdSupplier;
    private final Consumer<String> vcsTreeLoadedCallback;
    private final Supplier<Component> componentSupplier;

    // Keep track of user activity trackers for proper cleanup
    private final Map<Component, UserActivityTracker> trackerMap = new ConcurrentHashMap<>();

    public WindowPositionTracker(Supplier<String> tabIdSupplier,
                                 Consumer<String> vcsTreeLoadedCallback,
                                 Supplier<Component> componentSupplier) {
        this.tabIdSupplier = tabIdSupplier;
        this.vcsTreeLoadedCallback = vcsTreeLoadedCallback;
        this.componentSupplier = componentSupplier;
    }

    // Inner class to track scroll events
    private static class ScrollEvent {
        final boolean isUserEvent;
        final int position;
        final int eventCount;
        final long timestamp;

        ScrollEvent(boolean isUserEvent, int position, int eventCount) {
            this.isUserEvent = isUserEvent;
            this.position = position;
            this.eventCount = eventCount;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            String eventType = isUserEvent ? "User-scroll" : "Non-user";
            return eventType + " position: " + position + " (events " + eventCount + ")";
        }
    }

    // Current event tracking for aggregating consecutive events of the same type
    private static class CurrentEventTracker {
        boolean isUserEvent;
        int eventCount;
        int lastPosition;
        boolean hasEvents;

        CurrentEventTracker() {
            reset();
        }

        void reset() {
            isUserEvent = false;
            eventCount = 0;
            lastPosition = 0;
            hasEvents = false;
        }

        void addEvent(boolean userEvent, int position) {
            if (!hasEvents || isUserEvent != userEvent) {
                // Starting a new event group
                isUserEvent = userEvent;
                eventCount = 1;
                lastPosition = position;
                hasEvents = true;
            } else {
                // Continuing the same event group
                eventCount++;
                lastPosition = position;
            }
        }

        ScrollEvent createScrollEvent() {
            return new ScrollEvent(isUserEvent, lastPosition, eventCount);
        }
    }

    // Per-tab current event tracking
    private final Map<String, CurrentEventTracker> currentEventTrackerPerTab = new ConcurrentHashMap<>();

    public void attachScrollListeners(Component component) {
        String currentTabId = tabIdSupplier.get();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Attempting to attach scroll listeners for tab " + currentTabId);
        }

        // Clean up any existing retry timer
        if (retryTimer != null) {
            retryTimer.stop();
            retryTimer = null;
        }

        // Use invokeLater to ensure component is fully initialized
        SwingUtilities.invokeLater(() -> {
            try {
                JScrollPane scrollPane = findScrollPaneInComponent(component);
                if (scrollPane != null) {
                    currentScrollPaneRef = new WeakReference<>(scrollPane);
                    attachScrollListenersToScrollPane(scrollPane, false);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("No scroll pane found for attaching listeners in tab " + currentTabId);
                    }
                    // If no scroll pane found immediately, try again after a short delay
                    retryTimer = new Timer(100, e -> {
                        JScrollPane retryScrollPane = findScrollPaneInComponent(component);
                        if (retryScrollPane != null) {
                            currentScrollPaneRef = new WeakReference<>(retryScrollPane);
                            attachScrollListenersToScrollPane(retryScrollPane, true);
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Still no scroll pane found after retry for tab " + currentTabId);
                            }
                        }
                        retryTimer = null;
                    });
                    retryTimer.setRepeats(false);
                    retryTimer.start();
                }
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error attaching scroll listeners for tab " + currentTabId + ": " + e.getMessage());
                }
                LOG.warn("Error attaching scroll listeners", e);
            }
        });
    }

    private void attachScrollListenersToScrollPane(JScrollPane scrollPane, boolean isRetry) {
        String currentTabId = tabIdSupplier.get();
        String logPrefix = isRetry ? " (retry)" : "";

        try {
            // Remove any existing listeners to avoid duplicates
            removeAllListeners(scrollPane);

            // Track user activity (mouse and keyboard) on the scroll pane
            UserActivityTracker userActivityTracker = new UserActivityTracker();
            addUserActivityTracker(scrollPane, userActivityTracker);

            // Also add listeners to the scrollbars themselves
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

            if (verticalScrollBar != null) {
                addUserActivityTracker(verticalScrollBar, userActivityTracker);
            }
            if (horizontalScrollBar != null) {
                addUserActivityTracker(horizontalScrollBar, userActivityTracker);
            }

            // Also add keyboard listeners to the viewport to catch keyboard scrolling
            Component viewport = scrollPane.getViewport().getView();
            if (viewport != null) {
                viewport.addKeyListener(userActivityTracker);
                trackerMap.put(viewport, userActivityTracker);
                // Make sure the component can receive keyboard focus
                viewport.setFocusable(true);
            }

            // Create the scroll listener that will handle VCS tree loading detection and saving
            AdjustmentListener scrollListener = e -> {
                if (!e.getValueIsAdjusting()) {
                    // Count the events and track transitions
                    boolean hasUserActivity = userActivityTracker.hasRecentUserActivity();
                    int scrollPosition = e.getValue();

                    // Track the event
                    trackScrollEvent(currentTabId, hasUserActivity, scrollPosition);

                    if (hasUserActivity) {
                        userScrollEvents.incrementAndGet();
                    } else {
                        nonUserScrollEvents.incrementAndGet();
                    }

                    // Check if this is a scroll to position 0 without user activity - this indicates VCS tree has finished loading
                    if (scrollPosition == 0 && !hasUserActivity) {
                        handleVcsTreeLoaded();
                    }
                    // Only save if there was recent user activity (actual user scrolling via mouse or keyboard)
                    else if (hasUserActivity) {
                        scheduleScrollPositionSave();
                    }
                }
            };

            // Add listeners to both scrollbars
            if (verticalScrollBar != null) {
                verticalScrollBar.addAdjustmentListener(scrollListener);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Attached vertical scroll listener" + logPrefix + " for tab " + currentTabId);
                }
            }
            if (horizontalScrollBar != null) {
                horizontalScrollBar.addAdjustmentListener(scrollListener);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Attached horizontal scroll listener" + logPrefix + " for tab " + currentTabId);
                }
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error attaching scroll listeners" + logPrefix + " for tab " + currentTabId + ": " + e.getMessage());
            }
            LOG.warn("Error attaching scroll listeners", e);
        }
    }

    private void addUserActivityTracker(Component component, UserActivityTracker tracker) {
        component.addMouseListener(tracker);
        component.addMouseMotionListener(tracker);
        component.addMouseWheelListener(tracker);
        component.addKeyListener(tracker);
        trackerMap.put(component, tracker);
    }

    private void trackScrollEvent(String tabId, boolean isUserEvent, int position) {
        // Get or create the current event tracker for this tab
        CurrentEventTracker currentTracker = currentEventTrackerPerTab.computeIfAbsent(tabId, k -> new CurrentEventTracker());

        // Check if this is a different type of event than the current one
        if (currentTracker.hasEvents && currentTracker.isUserEvent != isUserEvent) {
            // Save the current event group to history
            List<ScrollEvent> history = scrollEventHistoryPerTab.computeIfAbsent(tabId, k -> new ArrayList<>());

            // Implement size limit for scroll event history
            if (history.size() >= MAX_SCROLL_EVENTS_PER_TAB) {
                history.removeFirst(); // Remove oldest event
            }

            history.add(currentTracker.createScrollEvent());

            // Start a new event group
            currentTracker.reset();
        }

        // Add this event to the current group
        currentTracker.addEvent(isUserEvent, position);
    }

    private void finalizeCurrentScrollEvent(String tabId) {
        CurrentEventTracker currentTracker = currentEventTrackerPerTab.get(tabId);
        if (currentTracker != null && currentTracker.hasEvents) {
            // Save the current event group to history
            List<ScrollEvent> history = scrollEventHistoryPerTab.computeIfAbsent(tabId, k -> new ArrayList<>());

            // Implement size limit for scroll event history
            if (history.size() >= MAX_SCROLL_EVENTS_PER_TAB) {
                history.removeFirst(); // Remove oldest event
            }

            history.add(currentTracker.createScrollEvent());
            currentTracker.reset();
        }
    }

    private void logScrollEventHistory(String tabId) {
        if (LOG.isDebugEnabled()) {
            // Finalize any current event
            finalizeCurrentScrollEvent(tabId);

            List<ScrollEvent> history = scrollEventHistoryPerTab.get(tabId);
            if (history != null && !history.isEmpty()) {
                LOG.debug("Scroll events for tab " + tabId + ":");
                for (ScrollEvent event : history) {
                    LOG.debug(event.toString());
                }
            }
        }
    }

    private void handleVcsTreeLoaded() {
        String currentTabId = tabIdSupplier.get();
        if (LOG.isDebugEnabled()) {
            LOG.debug("*** VCS TREE FINISHED LOADING *** for tab " + currentTabId);
        }

        // Log the scroll event history
        logScrollEventHistory(currentTabId);

        // Notify the callback
        if (vcsTreeLoadedCallback != null) {
            vcsTreeLoadedCallback.accept(currentTabId);
        }

        // Restore the saved scroll position
        ScrollPosition savedPosition = scrollPositionPerTab.get(currentTabId);
        if (savedPosition != null && savedPosition.isValid) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Restoring scroll position after VCS tree load for tab " + currentTabId +
                        " - vertical: " + savedPosition.verticalValue + ", horizontal: " + savedPosition.horizontalValue);
            }

            // Restore the position with a small delay to ensure the component is fully rendered
            SwingUtilities.invokeLater(() -> {
                Component component = componentSupplier.get();
                if (component != null) {
                    restoreScrollPosition(component, savedPosition);
                }
            });
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No saved scroll position to restore for tab " + currentTabId);
            }
        }
    }

    private void removeAllListeners(JScrollPane scrollPane) {
        if (scrollPane != null) {
            String currentTabId = tabIdSupplier.get();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Removing all listeners for tab " + currentTabId);
            }

            // Remove adjustment listeners
            removeScrollListeners(scrollPane);

            // Remove user activity trackers
            removeUserActivityTrackers(scrollPane);
            removeUserActivityTrackers(scrollPane.getVerticalScrollBar());
            removeUserActivityTrackers(scrollPane.getHorizontalScrollBar());

            Component viewport = scrollPane.getViewport().getView();
            if (viewport != null) {
                removeUserActivityTrackers(viewport);
            }
        }
    }

    private void removeUserActivityTrackers(Component component) {
        if (component != null) {
            UserActivityTracker tracker = trackerMap.remove(component);
            if (tracker != null) {
                component.removeMouseListener(tracker);
                component.removeMouseMotionListener(tracker);
                component.removeMouseWheelListener(tracker);
                component.removeKeyListener(tracker);
            }
        }
    }

    public void removeScrollListeners(JScrollPane scrollPane) {
        if (scrollPane != null) {
            String currentTabId = tabIdSupplier.get();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Removing scroll listeners for tab " + currentTabId);
            }

            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

            if (verticalScrollBar != null) {
                AdjustmentListener[] listeners = verticalScrollBar.getAdjustmentListeners();
                for (AdjustmentListener listener : listeners) {
                    verticalScrollBar.removeAdjustmentListener(listener);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Removed " + listeners.length + " vertical scroll listeners for tab " + currentTabId);
                }
            }
            if (horizontalScrollBar != null) {
                AdjustmentListener[] listeners = horizontalScrollBar.getAdjustmentListeners();
                for (AdjustmentListener listener : listeners) {
                    horizontalScrollBar.removeAdjustmentListener(listener);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Removed " + listeners.length + " horizontal scroll listeners for tab " + currentTabId);
                }
            }
        }
    }

    private void scheduleScrollPositionSave() {
        // Don't save if we haven't restored the scroll position yet
        if (!scrollPositionRestored) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Skipping scroll position save - not yet restored for tab " + tabIdSupplier.get());
            }
            return;
        }

        // Cancel any existing timer
        if (scrollSaveTimer != null) {
            scrollSaveTimer.stop();
        }

        // Start new timer
        scrollSaveTimer = new Timer(SCROLL_SAVE_DELAY_MS, e -> {
            saveScrollPositionDelayed();
            scrollSaveTimer = null;
        });
        scrollSaveTimer.setRepeats(false);
        scrollSaveTimer.start();
    }

    private void saveScrollPositionDelayed() {
        // Double-check we're still allowed to save
        if (!scrollPositionRestored) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Skipping delayed scroll position save - not yet restored for tab " + tabIdSupplier.get());
            }
            return;
        }

        String currentTabId = tabIdSupplier.get();
        ScrollPosition position = saveScrollPosition();
        if (position.isValid) {
            scrollPositionPerTab.put(currentTabId, position);

            // Log the save with event statistics and history
            if (LOG.isDebugEnabled()) {
                long userEvents = userScrollEvents.get();
                long nonUserEvents = nonUserScrollEvents.get();
                LOG.debug("Delayed save of scroll position for tab " + currentTabId +
                        " - vertical: " + position.verticalValue + ", horizontal: " + position.horizontalValue +
                        " | Total user events: " + userEvents + ", Total non-user events: " + nonUserEvents);

                // Also log the current scroll event history
                logScrollEventHistory(currentTabId);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not save scroll position for tab " + currentTabId + " - position invalid");
            }
        }
    }

    public ScrollPosition saveScrollPosition() {
        try {
            Component component = componentSupplier.get();
            if (component != null) {
                JScrollPane scrollPane = findScrollPaneInComponent(component);
                if (scrollPane != null) {
                    JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
                    JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

                    // Check that both scrollbars are not null before setting valid to true
                    boolean valid = verticalScrollBar != null && horizontalScrollBar != null;
                    int verticalValue = verticalScrollBar != null ? verticalScrollBar.getValue() : 0;
                    int horizontalValue = horizontalScrollBar != null ? horizontalScrollBar.getValue() : 0;

                    return new ScrollPosition(verticalValue, horizontalValue, valid);
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not save scroll position", e);
        }
        return ScrollPosition.invalid();
    }

    public void restoreScrollPosition(Component component, ScrollPosition position) {
        if (!position.isValid) {
            return;
        }

        String currentTabId = tabIdSupplier.get();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Restoring scroll position for tab " + currentTabId +
                    " - vertical: " + position.verticalValue + ", horizontal: " + position.horizontalValue);
        }

        JScrollPane scrollPane = findScrollPaneInComponent(component);
        if (scrollPane != null) {
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

            if (verticalScrollBar != null && position.verticalValue != 0) {
                verticalScrollBar.setValue(position.verticalValue);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Restored vertical scroll to " + position.verticalValue + " for tab " + currentTabId);
                }
            }
            if (horizontalScrollBar != null && position.horizontalValue != 0) {
                horizontalScrollBar.setValue(position.horizontalValue);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Restored horizontal scroll to " + position.horizontalValue + " for tab " + currentTabId);
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No scroll pane found for restoration in tab " + currentTabId);
            }
        }
    }

    private JScrollPane findScrollPaneInComponent(Component component) {
        if (component instanceof JScrollPane) {
            return (JScrollPane) component;
        }
        return findScrollPaneInChildren(component);
    }

    private JScrollPane findScrollPaneInChildren(Component component) {
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JScrollPane result = findScrollPaneInComponent(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    // Getters and setters for state
    public ScrollPosition getSavedScrollPosition(String tabId) {
        return scrollPositionPerTab.get(tabId);
    }

    public void setSavedScrollPosition(String tabId, ScrollPosition position) {
        scrollPositionPerTab.put(tabId, position);
    }

    public boolean isScrollPositionRestored() {
        return scrollPositionRestored;
    }

    public void setScrollPositionRestored(boolean restored) {
        this.scrollPositionRestored = restored;
        if (restored) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Enabled scroll position saving for tab " + tabIdSupplier.get());
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reset scroll position restoration flag for tab " + tabIdSupplier.get());
            }
        }
    }

    /**
     * Cleans up specific tab data to prevent memory leaks when tabs are closed.
     * This method should be called when a tab is closed or no longer needed.
     */
    public void cleanupTab(String tabId) {
        if (tabId != null) {
            scrollPositionPerTab.remove(tabId);
            scrollEventHistoryPerTab.remove(tabId);
            currentEventTrackerPerTab.remove(tabId);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Cleaned up data for tab " + tabId);
            }
        }
    }

    /**
     * Performs complete cleanup of all resources.
     * This method should be called when the tracker is no longer needed.
     */
    public void cleanup() {
        // Cancel timers
        if (scrollSaveTimer != null) {
            scrollSaveTimer.stop();
            scrollSaveTimer = null;
        }

        if (retryTimer != null) {
            retryTimer.stop();
            retryTimer = null;
        }

        // Remove all listeners
        JScrollPane currentScrollPane = currentScrollPaneRef.get();
        if (currentScrollPane != null) {
            removeAllListeners(currentScrollPane);
        }

        // Clear all tracker references
        trackerMap.clear();
        currentScrollPaneRef.clear();

        // Clear all data structures
        scrollPositionPerTab.clear();
        scrollEventHistoryPerTab.clear();
        currentEventTrackerPerTab.clear();

        // Reset counters and flags
        userScrollEvents.set(0);
        nonUserScrollEvents.set(0);
        scrollPositionRestored = false;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Completed full cleanup of WindowPositionTracker");
        }
    }

    // Inner class to track user activity (mouse and keyboard)
    private static class UserActivityTracker implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
        private volatile long lastUserActivity = 0;

        private void recordUserActivity() {
            lastUserActivity = System.currentTimeMillis();
        }

        public boolean hasRecentUserActivity() {
            return (System.currentTimeMillis() - lastUserActivity) < USER_ACTIVITY_TIMEOUT;
        }

        // Mouse events
        @Override
        public void mousePressed(MouseEvent e) {
            recordUserActivity();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            recordUserActivity();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            recordUserActivity();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            recordUserActivity();
        }

        // Keyboard events - track key presses that could cause scrolling
        @Override
        public void keyPressed(KeyEvent e) {
            // Track keyboard events that typically cause scrolling
            int keyCode = e.getKeyCode();
            if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN ||
                    keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT ||
                    keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN ||
                    keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END ||
                    keyCode == KeyEvent.VK_SPACE) {
                recordUserActivity();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            // Track key releases for the same keys
            int keyCode = e.getKeyCode();
            if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN ||
                    keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT ||
                    keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN ||
                    keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END ||
                    keyCode == KeyEvent.VK_SPACE) {
                recordUserActivity();
            }
        }

        @Override
        public void keyTyped(KeyEvent e) {
            // Track typing events - these indicate user is searching/typing
            recordUserActivity();
        }

        // Other events we don't need to track for scrolling
        @Override public void mouseClicked(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}
        @Override public void mouseMoved(MouseEvent e) {}
    }

    // Simple data class to hold scroll position
    public static class ScrollPosition {
        public final int verticalValue;
        public final int horizontalValue;
        public final boolean isValid;

        public ScrollPosition(int vertical, int horizontal, boolean valid) {
            this.verticalValue = vertical;
            this.horizontalValue = horizontal;
            this.isValid = valid;
        }

        public static ScrollPosition invalid() {
            return new ScrollPosition(0, 0, false);
        }
    }
}