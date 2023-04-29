package listener;

import com.intellij.openapi.project.Project;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import implementation.targetBranchWidget.PopUpFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseListener;

public class MyTabContentListener implements ContentManagerListener {

    private final Project project;

    public MyTabContentListener(Project project) {
        this.project = project;
//        this.viewService = project.getService(ViewService.class);
    }

    public void contentAdded(@NotNull ContentManagerEvent event) {
        System.out.println("contentAdded");
    }

    public void selectionChanged(@NotNull ContentManagerEvent event) {
        if (event.getOperation().equals(ContentManagerEvent.ContentOperation.add)) {
            System.out.println(event.getIndex());
            PopUpFactory pop = new PopUpFactory(project);
            pop.showPopup(new JButton());
        }
    }
}
