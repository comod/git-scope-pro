package implementation.targetBranchWidget;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.awt.RelativePoint;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class PopUpFactory {

    private final Project project;

    public PopUpFactory(Project project) {
        this.project = project;
    }

//    public void showPopup() {
//        MyGitBranchPopup myGitBranchPopup = getMyGitBranchPopup(DataManager.getInstance().getDataContext(e.getComponent()));
//        myGitBranchPopup.setPopupLastOpenedAt(PopUpFactory.class);
//    }

    public void showPopup(MouseEvent e) {
        MyGitBranchPopup myGitBranchPopup = getMyGitBranchPopup(DataManager.getInstance().getDataContext(e.getComponent()));
//                    MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, git.getRepository());
        myGitBranchPopup.setPopupLastOpenedAt(PopUpFactory.class);
//        showPopup(label);
        showPopup(e.getComponent());
    }


    public void showPopup(@NotNull AnActionEvent e) {

//        System.out.println("showPopup");
        MyGitBranchPopup myGitBranchPopup = getMyGitBranchPopup(e.getDataContext());
        Object lastOpenedAt = myGitBranchPopup.getLastOpenedAt();

//        System.out.println(lastOpenedAt);


        if (lastOpenedAt == PopUpFactory.class) {
//            System.out.println("instanceof TargetBranchPanel");
//            showPopupAtToolWindow();
            showPopup(e.getInputEvent().getComponent());
            return;
        }

        if (lastOpenedAt instanceof JList) {
//            System.out.println("instanceof JList");
            showPopup((Component) lastOpenedAt);
        }

    }

//    public void showPopupAtToolWindow() {
////        System.out.println("showPopupAtToolWindow");
//        showPopup(label);
//    }


    public void showPopup(Component component) {

//        System.out.println("showPopup(Component label)");


//        SwingUtilities.invokeLater(() -> {

        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        MyGitBranchPopup myGitBranchPopup = getMyGitBranchPopup(dataContext);
        ListPopup popup = myGitBranchPopup.asListPopup();

//            if (component == null) {
        popup.showUnderneathOf(component);
//            }else{
//        popup.showInBestPositionFor(dataContext);
//            }

        Object lastOpenedAt = myGitBranchPopup.getLastOpenedAt();

        if (lastOpenedAt instanceof JList) {
            myGitBranchPopup.setPopupLastOpenedAtList();
        }

//        });
    }

    @NotNull
    private MyGitBranchPopup getMyGitBranchPopup(DataContext e) {
        GitVcsSettings mySettings = GitVcsSettings.getInstance(project);
        @Nullable GitRepository repository = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
        assert repository != null;
        MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, repository, e);
        return myGitBranchPopup;
    }

    public void update() {
//        this.label.setText(manager.getTargetBranchDisplay());
    }

    public void addListener() {
    }

}
