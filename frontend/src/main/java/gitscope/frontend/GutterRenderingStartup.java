package gitscope.frontend;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Triggers eager initialization of {@link GutterRenderingService} on project open
 * so it registers as a listener on {@link service.GutterDataService} before any
 * ranges are published.
 */
public class GutterRenderingStartup implements ProjectActivity {
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        project.getService(GutterRenderingService.class);
        return Unit.INSTANCE;
    }
}
