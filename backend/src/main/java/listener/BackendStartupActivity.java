package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import service.ViewService;

/**
 * Fallback startup trigger for ViewService initialization.
 * <p>
 * In split mode (backend process), {@code ToolWindowManagerListener.RegisterToolWindow}
 * fires only on the frontend process, so {@code MyToolWindowListener} never receives it
 * and {@code ViewService.eventToolWindowReady()} is never called.
 * <p>
 * This activity runs after the project is fully initialized (tool windows registered,
 * VCS mappings loaded), ensuring both readiness gates are satisfied regardless of mode.
 */
public class BackendStartupActivity implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        ViewService viewService = project.getService(ViewService.class);
        if (viewService != null) {
            viewService.eventToolWindowReady();
        }
        return Unit.INSTANCE;
    }
}
