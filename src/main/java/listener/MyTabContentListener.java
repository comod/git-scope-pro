package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import service.ViewService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

import static service.ViewService.PLUS_TAB_LABEL;

public class MyTabContentListener implements ContentManagerListener {

    private final Project project;
    // Use lazy initialization for the service
    private ViewService viewService;

    public MyTabContentListener(Project project) {
        this.project = project;
    }
    
    private ViewService getViewService() {
        if (viewService == null) {
            viewService = project.getService(ViewService.class);
        }
        return viewService;
    }

    public void contentAdded(@NotNull ContentManagerEvent event) {
    }

    public void selectionChanged(@NotNull ContentManagerEvent event) {
        ContentManagerEvent.ContentOperation operation = event.getOperation();
        ContentManagerEvent.ContentOperation add = ContentManagerEvent.ContentOperation.add;
        if (operation.equals(add)) {
            ViewService service = getViewService(); // Get service only when needed
            service.setTabIndex(event.getIndex());
            @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
            if (Objects.equals(tabName, PLUS_TAB_LABEL)) {
                SwingUtilities.invokeLater(service::plusTabClicked);
                return;
            }

            service.setTabIndex(event.getIndex());
            service.setActiveModel();
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