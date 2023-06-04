package listener;

import com.intellij.openapi.project.Project;
import event.ChangeActionNotifierInterface;
import service.ViewService;
import model.MyModel;

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
