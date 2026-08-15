package listener;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;
import service.ViewService;
import system.Defs;

import java.util.List;

/**
 * Entry point of the refresh pipeline for filesystem changes. When the scope appears "stuck" this
 * is the first thing to check: if no line is logged here the event never reached the plugin, and
 * everything downstream is irrelevant.
 */
public class MyBulkFileListener implements BulkFileListener {

    private static final Logger LOG = Defs.getLogger(MyBulkFileListener.class);

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        if (events.isEmpty()) return;

        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {
            if (project.isDisposed()) continue;

            ViewService viewService = project.getService(ViewService.class);
            if (viewService == null) {
                LOG.debug("VFS batch of " + events.size() + " event(s): no ViewService for project "
                        + project.getName() + ", nothing refreshed");
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("VFS batch of " + events.size() + " event(s) -> collectChanges for project "
                        + project.getName() + ", first=" + events.get(0).getPath());
            }
            // TODO: collectChanges: bulk file event (disabled)
            viewService.collectChanges(true);
        }
    }
}
