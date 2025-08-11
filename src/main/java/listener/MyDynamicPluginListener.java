package listener;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import service.ViewService;

public class MyDynamicPluginListener implements DynamicPluginListener {

    public MyDynamicPluginListener() {
    }

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) continue;
            ViewService viewService = project.getService(ViewService.class);
            if (viewService != null) {
                // do whatever is needed per project before unload
            }
        }
    }

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) continue;
            ViewService viewService = project.getService(ViewService.class);
            if (viewService != null) {
                // do whatever is needed per project after load
            }
        }
    }


}
