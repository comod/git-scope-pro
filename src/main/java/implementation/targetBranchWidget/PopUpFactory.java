package implementation.targetBranchWidget;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
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

    public void showPopup(MouseEvent e) {
        GitVcsSettings mySettings = GitVcsSettings.getInstance(project);
        @Nullable GitRepository repository = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
//                    GitBranchesUsageCollector.branchWidgetClicked();
        assert repository != null;
        MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(e.getComponent()));
//                    MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, git.getRepository());
        myGitBranchPopup.setPopupLastOpenedAt(PopUpFactory.class);
//        showPopup(label);
        showPopup(e.getComponent());
    }

    public void showPopup(@NotNull AnActionEvent e) {

//        System.out.println("showPopup");
        GitVcsSettings mySettings = GitVcsSettings.getInstance(project);
        @Nullable GitRepository repository = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
//        GitBranchesUsageCollector.branchWidgetClicked();
        assert repository != null;
        MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, repository, e.getDataContext());
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

    public void showPopup(Component label) {

//        System.out.println("showPopup(Component label)");

        SwingUtilities.invokeLater(() -> {

            GitVcsSettings mySettings = GitVcsSettings.getInstance(project);
            @Nullable GitRepository repository = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
//            GitBranchesUsageCollector.branchWidgetClicked();
            assert repository != null;
            MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(label));
            ListPopup popup = myGitBranchPopup.asListPopup();
            popup.showUnderneathOf(label);

            Object lastOpenedAt = myGitBranchPopup.getLastOpenedAt();

            if (lastOpenedAt instanceof JList) {
                myGitBranchPopup.setPopupLastOpenedAtList();
            }

        });
    }

    public void update() {
//        this.label.setText(manager.getTargetBranchDisplay());
    }

    public void addListener() {
    }

}
