package ui.elements;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import implementation.Manager;
import implementation.targetBranchWidget.MyGitBranchPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Git;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.function.Consumer;

public class TargetBranchPanel extends JPanel implements Element {

    private final Project project;
    private final Git git;
    private Manager manager;

    JButton label = new JButton("loading...");

    private Consumer<String> consumer;

    public TargetBranchPanel(Project project, Git git) {

        this.project = project;
        this.git = git;

        this.createElement();
        this.addListener();

        this.manager = project.getService(Manager.class);

    }

    public void createElement() {

        FlowLayout layout = (FlowLayout) this.getLayout();
        layout.setVgap(0);

        label.setBorderPainted(false);
        label.setFocusable(false);

        label.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {

            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setBorderPainted(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setBorderPainted(false);
            }
        });

    }

    public void createElementLater() {

//        SwingUtilities.invokeLater(() -> {

            add(label);

            label.addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {

                    GitVcsSettings mySettings = GitVcsSettings.getInstance(project);
                    @Nullable GitRepository repository = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
//                    GitBranchesUsageCollector.branchWidgetClicked();
                    assert repository != null;
                    MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(e.getComponent()));
//                    MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, git.getRepository());
                    myGitBranchPopup.setPopupLastOpenedAt(TargetBranchPanel.class);
                    showPopup(label);
                }

                @Override
                public void mousePressed(MouseEvent e) {

                }

                @Override
                public void mouseReleased(MouseEvent e) {

                }

                @Override
                public void mouseEntered(MouseEvent e) {

                }

                @Override
                public void mouseExited(MouseEvent e) {

                }
            });

//        });

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


        if (lastOpenedAt == TargetBranchPanel.class) {
//            System.out.println("instanceof TargetBranchPanel");
            showPopupAtToolWindow();
            return;
        }

        if (lastOpenedAt instanceof JList) {
//            System.out.println("instanceof JList");
            showPopup((Component) lastOpenedAt);
        }

    }

    public void showPopupAtToolWindow() {
//        System.out.println("showPopupAtToolWindow");
        showPopup(label);
    }

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
        this.label.setText(manager.getTargetBranchDisplay());
    }

    public void addListener() {
    }

}
