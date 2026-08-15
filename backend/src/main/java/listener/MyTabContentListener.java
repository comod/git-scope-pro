package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;
import service.ViewService;
import service.ToolWindowServiceInterface;
import system.Defs;
import toolwindow.elements.VcsTree;

import javax.swing.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import static service.ViewService.PLUS_TAB_LABEL;

public class MyTabContentListener implements ContentManagerListener {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(MyTabContentListener.class);

    private final Project project;
    // Use lazy initialization for the service
    private ViewService viewService;
    private ToolWindowServiceInterface toolWindowService;

    public MyTabContentListener(Project project) {
        this.project = project;
    }
    
    private ViewService getViewService() {
        if (viewService == null) {
            viewService = project.getService(ViewService.class);
        }
        return viewService;
    }
    
    private ToolWindowServiceInterface getToolWindowService() {
        if (toolWindowService == null) {
            toolWindowService = project.getService(ToolWindowServiceInterface.class);
        }
        return toolWindowService;
    }

    /**
     * Contents that were removed while the plugin was not reordering them. A drag re-adds the very
     * same Content object, so seeing one come back identifies a move; a genuinely closed Content is
     * never added again, and the weak set lets it be collected without bookkeeping.
     */
    private final Set<Content> removedContents =
            Collections.newSetFromMap(new WeakHashMap<>());

    public void contentAdded(@NotNull ContentManagerEvent event) {
        // Deliberately not time-based: the platform can complete a drag several EDT ticks after the
        // removal, so anything that checks "is it back yet?" on a timer misses the slow cases and
        // leaves the tab's model deleted.
        if (!removedContents.remove(event.getContent())) {
            return;
        }

        ViewService viewService = getViewService();
        if (viewService == null || viewService.isDisposed()) return;

        LOG.debug("Tab '" + event.getContent().getTabName() + "' came back after removal - "
                + "it was dragged, not closed; resyncing tab order");
        viewService.onTabsDragged();
    }

    public void selectionChanged(@NotNull ContentManagerEvent event) {
        ContentManagerEvent.ContentOperation operation = event.getOperation();
        ContentManagerEvent.ContentOperation add = ContentManagerEvent.ContentOperation.add;

        @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
        if (operation.equals(add)) {
            ViewService service = getViewService(); // Get service only when needed

            // Skip during tab initialization — ViewService handles activation itself
            if (service.isTabInitializationInProgress()) return;

            service.setTabIndex(event.getIndex());
            
            if (Objects.equals(tabName, PLUS_TAB_LABEL)) {
                SwingUtilities.invokeLater(service::plusTabClicked);
                return;
            }

            service.setTabIndex(event.getIndex());
            service.setActiveModel();
            
            // Notify VcsTree about the tab switch
            SwingUtilities.invokeLater(() -> {
                try {
                    VcsTree vcsTree = getToolWindowService().getVcsTree();
                    if (vcsTree != null) {
                        vcsTree.onTabSwitched();
                    }
                } catch (Exception e) {
                    LOG.warn("MyTabContentListener: Error notifying VcsTree about tab switch: " + e.getMessage());
                }
            });
        }
    }

    public void contentRemoved(@NotNull ContentManagerEvent event) {
        @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
        if (Objects.equals(tabName, PLUS_TAB_LABEL)) {
            return;
        }

        // Don't remove the model if we're just reordering tabs
        ViewService viewService = getViewService();
        if (viewService == null || viewService.isProcessingTabReorder()) {
            return;
        }

        // Treat it as a close, exactly as before: this path also runs when tabs are torn down in
        // bulk (project close, tool window re-init), and its index check is what keeps those from
        // wiping the saved collection.
        viewService.removeTab(event.getIndex());

        // A tab dragged within the header is re-added at its new index, which arrives here as a
        // removal indistinguishable from a close, and the platform has no "content moved" event.
        // Remember the content: if it is added back, contentAdded recognises the move and rebuilds
        // the collection from the tab order, which restores the model removed just above -- it is
        // still reachable through the content, so nothing is lost.
        removedContents.add(event.getContent());
    }
}