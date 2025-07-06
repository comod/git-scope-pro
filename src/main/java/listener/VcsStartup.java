package listener;

import com.intellij.openapi.vcs.VcsMappingListener;
import com.intellij.openapi.project.Project;
import service.ViewService;

public class VcsStartup implements VcsMappingListener {
    private final Project project;
    public VcsStartup(Project project) {
        this.project = project;
    }
    @Override
    public void directoryMappingChanged() {
        ViewService viewService = project.getService(ViewService.class);
        viewService.eventVcsReady();
    }
}
