package toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import git4idea.branch.GitBranchType;
import org.jdesktop.swingx.StackLayout;
import org.jetbrains.annotations.NotNull;
import service.GitService;
import state.State;
import toolwindow.elements.BranchTree;
import toolwindow.elements.BranchTreeEntry;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BranchSelectView {

    //    public static final String TAG_OR_REVISION = "Tag or Revision...";
    //    private final JPanel rootPanel = new JPanel(new VerticalStackLayout());
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    //    private final JPanel rootPanel = new JPanel(new VerticalFlowLayout());
    //    private final MyModel myModel;
    private final Project project;
    //    private final TargetBranchService targetBranchService;
//    private final CurrentBranch currentBranch;
    private final GitService gitService;
    private final State state;

    private SearchTextField search;

    public BranchSelectView(Project project) {
        this.project = project;
        this.state = project.getService(State.class);
//        this.myModel = myModel;
//        this.targetBranchService = project.getService(TargetBranchService.class);
        this.gitService = project.getService(GitService.class);
//
//        this.currentBranch = new CurrentBranch(project);
//
//        myModel.getObservable().subscribe(model -> {
//            render();
//        });
//
////        draw();
//        drawNew();
//
//        addListener();
//
//        render();
//    }
//

        // main
        JPanel main = new JPanel();
        main.setLayout(new VerticalStackLayout());

        // Checkbox and Help-Icon
        JPanel help = new JPanel();
        help.setLayout(new FlowLayout(FlowLayout.LEFT));

        JCheckBox checkBox = new JCheckBox("Only Changes Since Common Ancestor (git diff A..B)");
        checkBox.setSelected(this.state.getThreeDotsCheckBox());
        checkBox.setBorder(JBUI.Borders.empty(10)); // top, left, bottom, right padding
        checkBox.addActionListener(e -> this.state.setThreeDotsCheckBox(checkBox.isSelected()));

        // Add a mouse listener to the help icon label to show the tool tip on hover
        checkBox.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent me) {
                checkBox.setToolTipText("Tick this box if you want to see the changes that were made only on this branch");
            }
        });

        help.add(checkBox);
        main.add(help);
//        SimpleColoredComponent simple = new SimpleColoredComponent();
//        simple.append("HEAD (Current)");
//        simple.setIcon(AllIcons.Vcs.Merge);
//        simple.setIconTextGap(20);
//        main.add(new SeparatorWithText());
//        @NotNull JBInsets insets = JBUI.insets(100, 10, 0, 0);
//        main.add(simple);
//        main.add(new SeparatorWithText());

        this.search = new SearchTextField();
        search.setText("");
        // node

        // branchTree

//        List<BranchTreeEntry> recentBranchList = new ArrayList<>();
//        recentBranchList.add(BranchTreeEntry.create("HEAD"));
//        recentBranchList.add(BranchTreeEntry.create("master"));

//        List<BranchTreeEntry> specialBranchList = new ArrayList<>();
//        specialBranchList.add(BranchTreeEntry.create("Tag or Revision..."));
//        Map<String, List<BranchTreeEntry>> specialNodes = new LinkedHashMap<>();
//        specialNodes.put(null, specialBranchList);

//        Map<String, List<BranchTreeEntry>> specialNodes = new LinkedHashMap<>();
//        specialNodes.put(TAG_OR_REVISION, null);
//        BranchTree specialBranchTree = createBranchTree(project, specialNodes);
//        main.add(specialBranchTree);

        Map<String, List<BranchTreeEntry>> node = new LinkedHashMap<>();
//        node.put("Recent", recentBranchList);
        boolean isMulti = gitService.isMulti();
//        if (isMulti) {
//            SeparatorWithText sep = new SeparatorWithText();
//            sep.setCaption("Repos");
//            main.add(sep);
//        }
        gitService.getRepositories().forEach(gitRepository -> {
            if (isMulti) {
                SeparatorWithText sep = new SeparatorWithText();
                sep.setCaption(gitRepository.getRoot().getName());
                main.add(sep);
            }
            List<BranchTreeEntry> localBranchList = gitService.listOfLocalBranches(gitRepository);
            if (!localBranchList.isEmpty()) {
                node.put(GitBranchType.LOCAL.getName(), localBranchList);
            }

            List<BranchTreeEntry> remoteBranchList = gitService.listOfRemoteBranches(gitRepository);
            if (!remoteBranchList.isEmpty()) {
                node.put(GitBranchType.REMOTE.getName(), remoteBranchList);
            }

            BranchTree branchTree = createBranchTree(project, node);
            main.add(branchTree);
        });

//        BranchTree branchTree2 = createBranchTree(project, node);
//        } else {

//            gitService.getRepositories().forEach(gitRepository -> {
//                List<BranchTreeEntry> localBranchList = gitService.iconLabelListOfLocalBranches(gitRepository);
//                node.put("Local", localBranchList);
//            });
//        }
//        node.put("Remote", localBranchList);

//        List<IconLabel> remoteBranchList = gitService.iconLabelListOfRemoteBranches();
//        BranchTree special = new BranchTree("Special", specialBranchList);


        // root = search + scroll (main)
        JBScrollPane scroll = new JBScrollPane(main);
        scroll.setBorder(JBUI.Borders.empty(JBUI.emptyInsets()));
        rootPanel.add(search, BorderLayout.NORTH);
        rootPanel.add(scroll, BorderLayout.CENTER);
    }

    @NotNull
    private BranchTree createBranchTree(Project project, Map<String, List<BranchTreeEntry>> node) {
        BranchTree branchTree = new BranchTree(project, node, search);

        search.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                branchTree.update(search);
            }
        });

        branchTree.getTreeComponent().addKeyListener(getKeyListener());
        return branchTree;
    }

    @NotNull
    private KeyListener getKeyListener() {
        return new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                //System.out.println(e);
                String text = search.getText();
                if (e.getKeyChar() == KeyEvent.VK_BACK_SPACE) {
                    //System.out.println(removeLastChar(text));
                    search.setText(removeLastChar(text));
                    return;
                }

                if (e.getKeyChar() == KeyEvent.VK_DELETE) {
                    return;
                }

                if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                    //System.out.println("enter");
                    return;
                }

                search.setText(text + e.getKeyChar());
            }

            @Override
            public void keyPressed(KeyEvent keyEvent) {

            }

            @Override
            public void keyReleased(KeyEvent keyEvent) {

            }
        };
    }

    private String removeLastChar(String str) {
        if (str != null && str.length() > 0) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

}
