package listener;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;
import service.ViewService;
import service.ToolWindowServiceInterface;
import toolwindow.elements.VcsTree;

import javax.swing.*;
import java.util.Objects;

import static service.ViewService.PLUS_TAB_LABEL;

public class MyTabContentListener implements ContentManagerListener {
    private static final Logger LOG = Logger.getInstance(MyTabContentListener.class);

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

    public void contentAdded(@NotNull ContentManagerEvent event) {
    }

    public void selectionChanged(@NotNull ContentManagerEvent event) {
        ContentManagerEvent.ContentOperation operation = event.getOperation();
        ContentManagerEvent.ContentOperation add = ContentManagerEvent.ContentOperation.add;

        @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
        if (operation.equals(add)) {
            ViewService service = getViewService(); // Get service only when needed
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
                    LOG.error("MyTabContentListener: Error notifying VcsTree about tab switch: " + e.getMessage());
                }
            });
        }
    }

    public void contentRemoved(@NotNull ContentManagerEvent event) {
        @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
        if (Objects.equals(tabName, PLUS_TAB_LABEL)) {
            return;
        }
        getViewService().removeTab(event.getIndex()); // Get service only when needed
    }
}