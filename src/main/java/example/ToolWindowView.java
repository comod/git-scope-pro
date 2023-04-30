package example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalBox;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ui.JBUI;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import implementation.targetBranchWidget.MyGitBranchPopup;
import implementation.targetBranchWidget.PopUpFactory;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import service.GitService;
import service.TargetBranchService;
import ui.elements.CurrentBranch;
import ui.elements.VcsTree;

import java.util.*;
import java.util.List;

public class ToolWindowView {

    //    private final JPanel rootPanel = new JPanel(new VerticalStackLayout());
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    //    private final JPanel rootPanel = new JPanel(new VerticalFlowLayout());
    private final MyModel myModel;
    private final Project project;
    private final TargetBranchService targetBranchService;
    private final CurrentBranch currentBranch;
    private final GitService gitService;

    JButton tbp = new JButton();

    private VcsTree vcsTree;
    private SearchTextField search;

    public ToolWindowView(Project project, MyModel myModel) {
        this.project = project;
//        this.state = State.getInstance(project);
        this.myModel = myModel;
        this.targetBranchService = project.getService(TargetBranchService.class);
        this.gitService = project.getService(GitService.class);

        this.currentBranch = new CurrentBranch(project);

        myModel.getObservable().subscribe(model -> {
            render();
        });

//        draw();
        drawNew();

        addListener();

        render();
    }

    protected MyGitBranchPopup getPopup(@NotNull Project project) {
        Component component = new JButton();
        GitVcsSettings mySettings = GitVcsSettings.getInstance(project);
        @Nullable GitRepository repository = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
        assert repository != null;
        return MyGitBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(component));

    }

    private void drawNew() {
        this.search = new SearchTextField();
        search.setText("hallo");

        // node
        Map<String, List<FavLabel>> node = new LinkedHashMap<>();

        // branchTree
        List<FavLabel> localBranchList = gitService.iconLabelListOfLocalBranches();

        List<FavLabel> recentBranchList = new ArrayList<>();
        recentBranchList.add(FavLabel.create("HEAD (Current)"));
        recentBranchList.add(FavLabel.create("master"));

        List<FavLabel> specialBranchList = new ArrayList<>();
        specialBranchList.add(FavLabel.create("HEAD (Current)"));
        specialBranchList.add(FavLabel.create(".."));
        specialBranchList.add(FavLabel.create("Tag or Revision..."));
        node.put("Recent", recentBranchList);
        node.put("Specific", specialBranchList);
        node.put("Local", localBranchList);
        node.put("Remote", localBranchList);

//        List<IconLabel> remoteBranchList = gitService.iconLabelListOfRemoteBranches();
//        BranchTree special = new BranchTree("Special", specialBranchList);

        BranchTree bt = new BranchTree(node);

        // apply
//        JPanel panel = new JPanel();
//        panel.setLayout(new VerticalStackLayout());
//        panel.setBorder(JBUI.Borders.empty(JBUI.emptyInsets()));
//        panel.add(special);
//        panel.add(bt);
        JBScrollPane scroll = new JBScrollPane(bt);
        scroll.setBorder(JBUI.Borders.empty(JBUI.emptyInsets()));
        rootPanel.add(search, BorderLayout.NORTH);
        rootPanel.add(scroll, BorderLayout.CENTER);
    }

    private void draw() {
        VerticalStackLayout verticalStackLayout = new VerticalStackLayout();
        rootPanel.setLayout(verticalStackLayout);
        VerticalBox vBox = new VerticalBox();
//        vBox.setAlignmentX(VerticalBox.LEFT_ALIGNMENT);
//        currentBranch.setAlignmentX(JLabel.LEFT_ALIGNMENT);
//        rootPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        HorizontalBox hBox = new HorizontalBox();
        hBox.setLayout(new GridBagLayout());
//        hBox.setAlignmentX(HorizontalBox.LEFT_ALIGNMENT);
        JPanel toolBarPanel = new JPanel();
        toolBarPanel.setLayout(new GridBagLayout());

//        toolBarPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JPanel spacer = new JPanel();
        toolBarPanel.add(currentBranch);
        toolBarPanel.add(tbp);
        hBox.add(toolBarPanel);


        vcsTree = new VcsTree(this.project);
        vcsTree.setLayout(new VerticalFlowLayout());

//        ActionGroup b = branchPicker();
//        b.getChildren()
        vBox.add(hBox);
        vBox.add(vcsTree);
        rootPanel.add(vBox);
//        rootPanel.add(hBox);
//        rootPanel.add(vcsTree);
    }

//    private List<TargetBranchAction> localBranchActions() {
//        GitRepository repository = (project.getService(GitService.class)).getRepository();
//        MyGitBranchPopupActions p = new MyGitBranchPopupActions(
//                project,
//                repository
//        );
//
//        return p.getWhat();
//    }

    private void addListener() {
        tbp.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                PopUpFactory f = project.getService(PopUpFactory.class);
                f.showPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {

            }
        });
    }

    private void render() {
//        if (myModel.getTargetBranch() == null) {
        tbp.setText(this.targetBranchService.getTargetBranchDisplay(myModel.getTargetBranch()));
//        } else {
//            tbp.hide();
//        }
        if (myModel.getChanges() == null) {
            return;
        }
//        vcsTree.update(myModel.getChanges());
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

}
