package listener;

import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.project.Project;
import service.ViewService;

public class VcsStartup implements VcsRepositoryMappingListener {
    private final Project project;

    public VcsStartup(Project project) {
//        this.project = project;
        this.project = project;
    }

    @Override
    public void mappingChanged() {
        //System.out.println("VcsStartup");

        ViewService viewService = project.getService(ViewService.class);
        viewService.eventVcsReady();
    }
}
