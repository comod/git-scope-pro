package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import service.ViewService;

public class MyChangeListListener implements ChangeListListener {

    private final ViewService viewService;
    public MyChangeListListener(Project project) {
        this.viewService = project.getService(ViewService.class);
    }

    public void changeListUpdateDone() {
        // TODO: collectChanges: VcsTree is updated
        viewService.incrementUpdate();
        viewService.collectChanges(true);
    }
}
