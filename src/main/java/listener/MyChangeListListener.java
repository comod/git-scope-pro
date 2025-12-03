package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import service.ViewService;
import system.Defs;

public class MyChangeListListener implements ChangeListListener {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(MyChangeListListener.class);

    private final ViewService viewService;
    public MyChangeListListener(Project project) {
        this.viewService = project.getService(ViewService.class);
    }

    public void changeListUpdateDone() {
        LOG.debug("changeListUpdateDone() called - triggering update");
        // TODO: collectChanges: VcsTree is updated
        viewService.incrementUpdate();
        viewService.collectChanges(true);
    }
}
