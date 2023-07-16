package listener;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import service.ViewService;

public class MyDynamicPluginListener implements DynamicPluginListener {
    private final ViewService viewService;

    public MyDynamicPluginListener(Project project) {
        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        //System.out.println(pluginDescriptor);
    }

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        //System.out.println("pluginLoaded" + pluginDescriptor);
//        viewService.init();
    }

}
