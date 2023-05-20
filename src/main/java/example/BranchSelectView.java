package example;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import service.GitService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BranchSelectView {

    //    private final JPanel rootPanel = new JPanel(new VerticalStackLayout());
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    //    private final JPanel rootPanel = new JPanel(new VerticalFlowLayout());
    //    private final MyModel myModel;
    private final Project project;
    //    private final TargetBranchService targetBranchService;
//    private final CurrentBranch currentBranch;
    private final GitService gitService;

    private SearchTextField search;

    public BranchSelectView(Project project) {
        this.project = project;
////        this.state = State.getInstance(project);
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
//        main.setLayout(new VerticalFlowLayout());
        main.setLayout(new VerticalStackLayout());


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
        Map<String, List<BranchTreeEntry>> node = new LinkedHashMap<>();

        // branchTree

//        List<BranchTreeEntry> recentBranchList = new ArrayList<>();
//        recentBranchList.add(BranchTreeEntry.create("HEAD"));
//        recentBranchList.add(BranchTreeEntry.create("master"));
//
//        node.put("HEAD", null);
//        node.put("Tag or Revision...", null);
//        node.put("Recent", recentBranchList);
        boolean isMulti = gitService.isMulti();
        gitService.getRepositories().forEach(gitRepository -> {
            if (isMulti) {
                SeparatorWithText sep = new SeparatorWithText();
                sep.setCaption(gitRepository.getRoot().getName());
                main.add(sep);
            }
            List<BranchTreeEntry> localBranchList = gitService.iconLabelListOfLocalBranches(gitRepository);
            node.put("Local", localBranchList);

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
                System.out.println(e);
                String text = search.getText();
                if (e.getKeyChar() == KeyEvent.VK_BACK_SPACE) {
                    System.out.println(removeLastChar(text));
                    search.setText(removeLastChar(text));
                    return;
                }

                if (e.getKeyChar() == KeyEvent.VK_DELETE) {
                    return;
                }

                if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                    System.out.println("enter");
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
