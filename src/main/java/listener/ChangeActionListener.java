package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import event.ChangeActionNotifierInterface;
import example.ViewService;
import model.MyModel;

import java.util.Collection;

public class ChangeActionListener implements ChangeActionNotifierInterface {


    private final ViewService viewService;

    public ChangeActionListener(Project project) {
        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void doAction(MyModel model) {
//        viewService.doUpdate(model);
    }
}
