package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;
import service.ViewService;

import java.util.List;

public class MyBulkFileListener implements BulkFileListener {

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        if (events.isEmpty()) return;

        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {
            if (project.isDisposed()) continue;

            ViewService viewService = project.getService(ViewService.class);
            if (viewService != null) {
                viewService.collectChanges();
            }
        }
    }
}