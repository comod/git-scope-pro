package state;

import system.Defs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WindowPositionTracker {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(WindowPositionTracker.class);
    private static final int SCROLL_SAVE_DELAY_MS = 500;
    private static final long USER_ACTIVITY_TIMEOUT = 2000; // 2 seconds

    // Per-tab scroll position tracking
    private final Map<String, ScrollPosition> scrollPositionPerTab = new ConcurrentHashMap<>();

    // Current state (using WeakReference to prevent memory leaks)
    private WeakReference<JScrollPane> currentScrollPaneRef = new WeakReference<>(null);
    private Timer scrollSaveTimer;
    private Timer retryTimer; // Keep reference to retry timer for cleanup
    private boolean scrollPositionRestored = false;

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

    public void attachScrollListeners(Component component) {
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
                    attachScrollListenersToScrollPane(scrollPane);
                } else {
                    // If no scroll pane found immediately, try again after a short delay
                    retryTimer = new Timer(100, e -> {
                        JScrollPane retryScrollPane = findScrollPaneInComponent(component);
                        if (retryScrollPane != null) {
                            currentScrollPaneRef = new WeakReference<>(retryScrollPane);
                            attachScrollListenersToScrollPane(retryScrollPane);
                        } else {
                            LOG.debug("No scroll pane found after retry for tab " + tabIdSupplier.get());
                        }
                        retryTimer = null;
                    });
                    retryTimer.setRepeats(false);
                    retryTimer.start();
                }
            } catch (Exception e) {
                LOG.debug("Error attaching scroll listeners", e);
            }
        });
    }

    private void attachScrollListenersToScrollPane(JScrollPane scrollPane) {
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
                    boolean hasUserActivity = userActivityTracker.hasRecentUserActivity();

                    // Check if this is a scroll to position 0 without user activity - this indicates VCS tree has finished loading
                    if (e.getValue() == 0 && !hasUserActivity) {
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
            }
            if (horizontalScrollBar != null) {
                horizontalScrollBar.addAdjustmentListener(scrollListener);
            }
        } catch (Exception e) {
            LOG.debug("Error attaching scroll listeners", e);
        }
    }

    private void addUserActivityTracker(Component component, UserActivityTracker tracker) {
        component.addMouseListener(tracker);
        component.addMouseMotionListener(tracker);
        component.addMouseWheelListener(tracker);
        component.addKeyListener(tracker);
        trackerMap.put(component, tracker);
    }

    private void handleVcsTreeLoaded() {
        String currentTabId = tabIdSupplier.get();

        // Notify the callback
        if (vcsTreeLoadedCallback != null) {
            vcsTreeLoadedCallback.accept(currentTabId);
        }

        // Restore the saved scroll position
        ScrollPosition savedPosition = scrollPositionPerTab.get(currentTabId);
        if (savedPosition != null && savedPosition.isValid) {
            // Restore the position with a small delay to ensure the component is fully rendered
            SwingUtilities.invokeLater(() -> {
                Component component = componentSupplier.get();
                if (component != null) {
                    restoreScrollPosition(component, savedPosition);
                }
            });
        }
    }

    private void removeAllListeners(JScrollPane scrollPane) {
        if (scrollPane != null) {
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
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

            if (verticalScrollBar != null) {
                for (AdjustmentListener listener : verticalScrollBar.getAdjustmentListeners()) {
                    verticalScrollBar.removeAdjustmentListener(listener);
                }
            }
            if (horizontalScrollBar != null) {
                for (AdjustmentListener listener : horizontalScrollBar.getAdjustmentListeners()) {
                    horizontalScrollBar.removeAdjustmentListener(listener);
                }
            }
        }
    }

    private void scheduleScrollPositionSave() {
        // Don't save if we haven't restored the scroll position yet
        if (!scrollPositionRestored) {
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
            return;
        }

        String currentTabId = tabIdSupplier.get();
        ScrollPosition position = saveScrollPosition();
        if (position.isValid) {
            scrollPositionPerTab.put(currentTabId, position);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Saved scroll position for tab " + currentTabId +
                        " - vertical: " + position.verticalValue + ", horizontal: " + position.horizontalValue);
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

        JScrollPane scrollPane = findScrollPaneInComponent(component);
        if (scrollPane == null) {
            LOG.debug("No scroll pane found for restoration in tab " + tabIdSupplier.get());
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Restoring scroll position for tab " + tabIdSupplier.get() +
                    " - vertical: " + position.verticalValue + ", horizontal: " + position.horizontalValue);
        }

        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();

        if (verticalScrollBar != null && position.verticalValue != 0) {
            verticalScrollBar.setValue(position.verticalValue);
        }
        if (horizontalScrollBar != null && position.horizontalValue != 0) {
            horizontalScrollBar.setValue(position.horizontalValue);
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
    }

    /**
     * Cleans up specific tab data to prevent memory leaks when tabs are closed.
     * This method should be called when a tab is closed or no longer needed.
     */
    public void cleanupTab(String tabId) {
        if (tabId != null) {
            scrollPositionPerTab.remove(tabId);
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

        scrollPositionRestored = false;
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
        public record ScrollPosition(int verticalValue, int horizontalValue, boolean isValid) {

        public static ScrollPosition invalid() {
                return new ScrollPosition(0, 0, false);
            }
        }
}