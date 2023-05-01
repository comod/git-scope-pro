package listener;

import com.intellij.openapi.project.Project;
import example.ViewService;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class TreeKeyListener implements KeyListener {
    private final Project project;
    private final ViewService viewService;

    public TreeKeyListener(Project project) {
        this.project = project;
        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void keyTyped(KeyEvent keyEvent) {
        System.out.println(keyEvent);
//        viewService.u();
    }

    @Override
    public void keyPressed(KeyEvent keyEvent) {

    }

    @Override
    public void keyReleased(KeyEvent keyEvent) {

    }
}
