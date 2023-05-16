package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import example.ViewService;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static example.ViewService.PLUS_TAB_LABEL;

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
        this.viewService.setTabIndex(event.getIndex());

        ContentManagerEvent.ContentOperation operation = event.getOperation();
        ContentManagerEvent.ContentOperation add = ContentManagerEvent.ContentOperation.add;
        if (operation.equals(add)) {
            @NlsContexts.TabTitle String tabName = event.getContent().getTabName();
            if (Objects.equals(tabName, PLUS_TAB_LABEL)) {
                this.viewService.plusTabClicked();
            }
        }

        this.viewService.setTabIndex(event.getIndex());

        if (operation.equals(add)) {
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
