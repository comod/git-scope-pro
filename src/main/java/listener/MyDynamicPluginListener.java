package listener;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import service.ViewService;
import implementation.compare.ChangesService;
import system.Defs;

/**
 * Handles dynamic plugin loading and unloading events.
 * This listener ensures proper cleanup when the plugin is unloaded/updated
 * and proper initialization when the plugin is loaded.
 */
public class MyDynamicPluginListener implements DynamicPluginListener {
    private static final Logger LOG = Defs.getLogger(MyDynamicPluginListener.class);

    public MyDynamicPluginListener() {
    }

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // Only handle our own plugin
        if (isAlienPlugin(pluginDescriptor)) {
            return;
        }

        LOG.info("Git Scope plugin unloading" + (isUpdate ? " (update)" : ""));

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) continue;

            try {
                // Save state before unloading
                ViewService viewService = project.getService(ViewService.class);
                if (viewService != null) {
                    viewService.save();
                }

                // Clear caches in ChangesService
                ChangesService changesService = project.getService(ChangesService.class);
                if (changesService != null) {
                    changesService.clearCache();
                }

                LOG.debug("Successfully prepared project '" + project.getName() + "' for plugin unload");
            } catch (Exception e) {
                LOG.error("Error preparing project for plugin unload: " + project.getName(), e);
            }
        }
    }

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        // Only handle our own plugin
        if (isAlienPlugin(pluginDescriptor)) {
            return;
        }

        LOG.info("Git Scope plugin loaded");

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) continue;

            try {
                // Reinitialize services if needed
                ViewService viewService = project.getService(ViewService.class);
                if (viewService != null) {
                    // Services are automatically initialized by the platform
                    // No explicit reinitialization needed
                }

                LOG.debug("Successfully initialized plugin for project: " + project.getName());
            } catch (Exception e) {
                LOG.error("Error initializing plugin for project: " + project.getName(), e);
            }
        }
    }

    /**
     * Checks if the plugin descriptor refers to some other plugin
     */
    private boolean isAlienPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        String pluginId = pluginDescriptor.getPluginId().getIdString();
        // Match the plugin ID from plugin.xml
        return !"Git Scope".equals(pluginId);
    }
}
