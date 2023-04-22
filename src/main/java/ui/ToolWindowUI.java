package ui;

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.messages.MessageBusConnection;
import implementation.Manager;
import org.jetbrains.annotations.NotNull;
import ui.elements.*;
import utils.Git;

import javax.swing.*;
import java.util.Collection;

public class ToolWindowUI {

    private final Manager manager;

    private Git git;

    private Project project;

    // Form Elements
    private JPanel eRootPanel; // Root Panel

    private JLabel eCurrentBranch;
    private JPanel eVcsTree;
    private JPanel eTargetBranchPanel;
    private JCheckBox eFeatureToggle;

    private VcsTree vcsTree;
    private FeatureToggle featureToggle;
    private TargetBranchPanel targetBranchPanel;
    private CurrentBranch currentBranch;

    private String lastTargetBranch = Git.BRANCH_HEAD;

    public ToolWindowUI(Project project, Git git, Manager manager) { //Consumer<String> callBack) {

        this.project = project;
        this.git = git;
        this.manager = manager;

        this.init();

    }

    public void init() {

        if (this.git.isReady()) {
            this.onInit();
            return;
        }

        this.project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, () -> {
            onInit();
        });

    }

    public void updateSourceBranch() {
        this.updateStatus();
    }

    private void createUIComponents() {

//        System.out.println("createUIComponents for ToolWindow");

        // Create
        FeatureToggle featureToggle = new FeatureToggle(manager);
        TargetBranchPanel targetBranchPanel = new TargetBranchPanel(this.project, this.git);

        VcsTree vcsTree = new VcsTree(this.project);
        CurrentBranch currentBranch = new CurrentBranch(this.git);

        // Assign Classes
        this.vcsTree = vcsTree;
        this.featureToggle = featureToggle;
        this.targetBranchPanel = targetBranchPanel;
        this.currentBranch = currentBranch;

        // Assign Elements
        this.eVcsTree = vcsTree;
        this.eFeatureToggle = featureToggle;
        this.eTargetBranchPanel = targetBranchPanel;
        this.eCurrentBranch = currentBranch;

    }

    public void onInit() {

        // Consider better event or keep in init in mind
        // @todo: test with add new branches

//        // @todo check invokeLater is relevant
        SwingUtilities.invokeLater(() -> {

            this.targetBranchPanel.createElementLater();

            // Set Initial branch
//            this.setSourceBranch(this.git.getBranchName());

            this.branchChangedListener();

            // Init the UI
            manager.initOnOpenToolWindow();

            // Do Initial Compare and Update
//            System.out.println("doCompareAndUpdate ToolWindowUI");

            if (manager.isInitialized()) {
                manager.doCompareAndUpdate();
                return;
            }
            manager.initialUpdate();

//            this.update();
//
        });

    }

    public void branchChangedListener() {

        MessageBusConnection connect = this.project.getMessageBus().connect();
        connect.subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, new BranchChangeListener() {

            @Override
            public void branchWillChange(@NotNull String branchName) {
//                setLoading();
            }

            @Override
            public void branchHasChanged(@NotNull String branchName) {
                SwingUtilities.invokeLater(() -> {

                    updateSourceBranch();

//                    System.out.println("doCompareAndUpdate branchHasChanged");
                    manager.doCompareAndUpdate();

                });
            }

        });
    }

    public Boolean isFeatureActive() {
        return this.featureToggle.isSelected();
    }

    public void setFeatureActive(Boolean selected) {
        this.featureToggle.setSelected(selected);
    }

    public void update() {

//        this.targetBranchPanel.setTargetBranch();

        // Status
        this.updateStatus();

        // TargetBranchPanel
        this.targetBranchPanel.update();

    }

    public void updateStatus() {
        this.currentBranch.update();
    }

    public void setLoading() {
        this.vcsTree.setLoading();
    }

    public void updateVcsTree(Collection<Change> changes) {
        this.vcsTree.update(changes);
    }

    public JPanel getRootPanel() {
        return this.eRootPanel;
    }

    public void showTargetBranchPopup(@NotNull AnActionEvent e) {
        try {
            this.targetBranchPanel.showPopup(e);
        } catch (Throwable ex) {
            // no exceptions
        }
    }

    public void showTargetBranchPopupAtToolWindow() {
        this.targetBranchPanel.showPopupAtToolWindow();
    }

}
