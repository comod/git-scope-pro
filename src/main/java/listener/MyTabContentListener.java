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
    private final ViewService viewService;

    public MyTabContentListener(Project project) {
        this.project = project;
        this.viewService = project.getService(ViewService.class);
    }

    public void contentAdded(@NotNull ContentManagerEvent event) {
        System.out.println("contentAdded");
    }

    public void selectionChanged(@NotNull ContentManagerEvent event) {

        ContentManagerEvent.ContentOperation operation = event.getOperation();
        ContentManagerEvent.ContentOperation add = ContentManagerEvent.ContentOperation.add;
        System.out.println("selectionChanged " + operation);
        if (operation.equals(add)) {
            this.viewService.setTabIndex(event.getIndex());
            @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
            if (Objects.equals(tabName, PLUS_TAB_LABEL)) {
                SwingUtilities.invokeLater(this.viewService::plusTabClicked);
                return;
            }

            this.viewService.setTabIndex(event.getIndex());
            this.viewService.setActiveModel();
        }
    }

    public void contentRemoved(@NotNull ContentManagerEvent event) {
        @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
        if (Objects.equals(tabName, PLUS_TAB_LABEL)) {
            return;
        }
        this.viewService.removeTab(event.getIndex());
    }
}
