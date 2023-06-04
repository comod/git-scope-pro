package statusBar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MyStatusBarWidgetFactory implements StatusBarWidgetFactory {
    @Override
    public @NotNull @NonNls String getId() {
        return "GitScopeStatusBar";
    }

    @Override
    public @NotNull @NlsContexts.ConfigurableName String getDisplayName() {
        return "Git Scope";
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new MyStatusBarWidget(project);
    }
}
