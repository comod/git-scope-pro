package listener;

import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.project.Project;
import implementation.Manager;
//import implementation.EventManager;
//import implementation.Manager;
//import service.ToolWindowServiceInterface;

public class VcsStartup implements VcsRepositoryMappingListener {
    private final Project project;

    public VcsStartup(Project project) {
//        this.project = project;
        this.project = project;
    }

    @Override
    public void mappingChanged() {
        System.out.println("VcsStartup");
        Manager manager = project.getService(Manager.class);
        manager.init(project);
//        EventManager manager = project.getService(EventManager.class);
//        manager.initA();


//        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
//        toolWindowService.addTab();
    }
}
