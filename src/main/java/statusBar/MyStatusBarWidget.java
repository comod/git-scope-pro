package statusBar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import service.StatusBarService;

import javax.swing.*;

public class MyStatusBarWidget implements CustomStatusBarWidget {
    private final StatusBarService statusBarService;

    public MyStatusBarWidget(Project project) {
        this.statusBarService = project.getService(StatusBarService.class);
    }

    @Nullable
    @Override
    public WidgetPresentation getPresentation() {
        return null;
    }

    @Override
    public JComponent getComponent() {
        return this.statusBarService.getPanel();
    }

    @NotNull
    @Override
    public String ID() {
        return "GitScopeStatusBarWidget";
    }
}
