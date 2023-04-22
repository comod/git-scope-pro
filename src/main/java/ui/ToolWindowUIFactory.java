package ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentFactoryImpl;
import implementation.Manager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ToolWindowUIFactory implements ToolWindowFactory {

    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        // Even on Start, or on open toolWindow

       Manager manager = project.getService(Manager.class);
        ToolWindowUI toolWindowUI = new ToolWindowUI(
                project,
                manager.getGit(),
                manager
        );
        manager.setToolWindowUI(toolWindowUI);

        ContentFactory contentFactory = new ContentFactoryImpl();
        Content content = contentFactory.createContent(toolWindowUI.getRootPanel(), "", false);
        toolWindow.getContentManager().addContent(content);

    }
}