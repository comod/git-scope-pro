package listener;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import service.ViewService;

public class ToggleHeadAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        ViewService viewService = e.getProject().getService(ViewService.class);
        viewService.toggleActionInvoked();
    }
}
