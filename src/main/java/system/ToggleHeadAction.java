package system;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import implementation.Manager;

public class ToggleHeadAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
//        Manager manager = ServiceManager.getService(e.getProject(), Manager.class);
        System.out.println("action");
//        manager.toggleHeadAction();
    }
}
