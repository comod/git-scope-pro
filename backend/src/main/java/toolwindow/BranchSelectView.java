package toolwindow;

import com.intellij.ide.HelpTooltipKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.ui.SeparatorFactory;
import git4idea.branch.GitBranchType;
import com.intellij.ui.treeStructure.Tree;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import service.GitService;
import state.State;
import toolwindow.elements.BranchTree;
import toolwindow.elements.BranchTreeEntry;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BranchSelectView {
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private final Project project;
    private final GitService gitService;
    private final State state;
    private final SearchTextField search;
    private final java.util.List<BranchTree> branchTrees = new java.util.ArrayList<>();

private JPanel createManualInputPanel(GitRepository repository, BranchTree branchTree) {
    JPanel manualInputPanel = new JPanel(new BorderLayout());
    manualInputPanel.setBorder(JBUI.Borders.empty(2, 8)); // top, left, bottom, right margins
    
    JBTextField manualInput = new JBTextField();
    HelpTooltipKt.setToolTipText(manualInput, HtmlChunk.tag("div").attr("style", "white-space: nowrap;").children(
            HtmlChunk.text("Enter any valid Git reference:"),
            HtmlChunk.ul().children(
                    HtmlChunk.li().addText("Branch names (e.g., main, develop, feature/xyz)"),
                    HtmlChunk.li().addText("Tag names (e.g., v1.0.0, release-2023)"),
                    HtmlChunk.li().addText("Special refs (e.g., HEAD, HEAD~1, HEAD~5)"),
                    HtmlChunk.li().addText("Commit hashes (full or abbreviated)"),
                    HtmlChunk.li().addText("Other Git syntax (e.g., @{upstream}, origin/main)")
            )
    ));
    manualInput.getEmptyText()
            .setText("Enter branch, tag, or git ref...")
            .setFont(manualInput.getFont().deriveFont(Font.ITALIC));
    
    manualInput.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                String ref = manualInput.getText().trim();
                if (!ref.isEmpty()) {
                    BranchTreeEntry entry = BranchTreeEntry.create(ref, false, repository);
                    Tree tree = (Tree) branchTree.getTreeComponent();

                    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
                    DefaultMutableTreeNode manualNode = findOrCreateManualInputNode(root);

                    DefaultMutableTreeNode entryNode = new DefaultMutableTreeNode(entry);
                    manualNode.add(entryNode);
                    branchTree.update(search);
                    TreePath path = new TreePath(entryNode.getPath());
                    tree.setSelectionPath(path);
                    manualInput.setText("");
                }
            }
        }
    });

    manualInputPanel.add(manualInput, BorderLayout.CENTER);
    return manualInputPanel;
}

    /** Finds the "Manual Input" child node under {@code root}, creating it if it doesn't exist yet. */
    private DefaultMutableTreeNode findOrCreateManualInputNode(DefaultMutableTreeNode root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if ("Manual Input".equals(child.getUserObject())) {
                return child;
            }
        }
        DefaultMutableTreeNode manualNode = new DefaultMutableTreeNode("Manual Input");
        root.add(manualNode);
        return manualNode;
    }


    public BranchSelectView(Project project) {
        this.project = project;
        this.state = project.getService(State.class);
        this.gitService = project.getService(GitService.class);

        // main
        JPanel main = new JPanel();
        main.setLayout(new VerticalStackLayout());

        // Checkbox and Help-Icon
        JPanel help = new JPanel();
        help.setLayout(new FlowLayout(FlowLayout.LEFT));

        JCheckBox checkBox = new JCheckBox("Only Changes Since Common Ancestor (git diff <selection>...HEAD)");
        HelpTooltipKt.setToolTipText(checkBox, HtmlChunk.fragment(
                HtmlChunk.text("Compares HEAD against its common ancestor with the selection,"), HtmlChunk.br(),
                HtmlChunk.text("so changes made on the selected branch since you branched off are excluded."), HtmlChunk.br(),
                HtmlChunk.text("This is the diff a pull request shows."), HtmlChunk.br(),
                HtmlChunk.text("Unchecked, the selection is compared directly to HEAD.")
        ));
        checkBox.setSelected(this.state.getThreeDotsCheckBox());
        checkBox.setBorder(JBUI.Borders.empty(1)); // top, left, bottom, right padding
        checkBox.addActionListener(e -> this.state.setThreeDotsCheckBox(checkBox.isSelected()));

        help.add(checkBox);
        main.add(help);

        this.search = new SearchTextField();
        search.setText("");

        boolean isMulti = gitService.isMulti();  // More than one repo
        gitService.getRepositoriesAsync(repositories -> {
            repositories.forEach(gitRepository -> {
                if (isMulti) {
                    JComponent sep = SeparatorFactory.createSeparator(gitRepository.getRoot().getName(), null);
                    main.add(sep);
                }

                Map<String, List<BranchTreeEntry>> node = new LinkedHashMap<>();
                List<BranchTreeEntry> localBranchList = gitService.listOfLocalBranches(gitRepository);
                if (!localBranchList.isEmpty()) {
                    node.put(GitBranchType.LOCAL.getName(), localBranchList);
                }

                List<BranchTreeEntry> remoteBranchList = gitService.listOfRemoteBranches(gitRepository);
                if (!remoteBranchList.isEmpty()) {
                    node.put(GitBranchType.REMOTE.getName(), remoteBranchList);
                }

                BranchTree branchTree = createBranchTree(project, node);
                branchTrees.add(branchTree);  // Track for cleanup
                main.add(createManualInputPanel(gitRepository, branchTree));
                main.add(branchTree);
            });
        });

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
                String text = search.getText();
                if (e.getKeyChar() == KeyEvent.VK_BACK_SPACE) {
                    search.setText(removeLastChar(text));
                    return;
                }

                if (e.getKeyChar() == KeyEvent.VK_DELETE) {
                    return;
                }

                if (e.getKeyChar() == KeyEvent.VK_ENTER) {
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
        if (str != null && !str.isEmpty()) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public void dispose() {
        // Clean up all branch trees (removes listeners)
        for (BranchTree branchTree : branchTrees) {
            if (branchTree != null) {
                branchTree.removeAll();
            }
        }
        branchTrees.clear();

        // Clean up root panel
        rootPanel.removeAll();
    }

}