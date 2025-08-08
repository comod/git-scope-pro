package toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout;
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
    private SearchTextField search;

private JPanel createManualInputPanel(GitRepository repository, BranchTree branchTree) {
    JPanel manualInputPanel = new JPanel(new BorderLayout());
    manualInputPanel.setBorder(JBUI.Borders.empty(2, 8)); // top, left, bottom, right margins
    
    JBTextField manualInput = new JBTextField();
    manualInput.setToolTipText(
            "<html>" +
                    "Enter any valid Git reference:<br/>" +
                    "• Branch names (e.g., main, develop, feature/xyz)<br/>" +
                    "• Tag names (e.g., v1.0.0, release-2023)<br/>" +
                    "• Special refs (e.g., HEAD, HEAD~1, HEAD~5)<br/>" +
                    "• Commit hashes (full or abbreviated)<br/>" +
                    "• Other Git syntax (e.g., @{upstream}, origin/main)" +
                    "</html>"
    );
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

                    // Find or create "Manual Input" node
                    DefaultMutableTreeNode manualNode = null;
                    for (int i = 0; i < root.getChildCount(); i++) {
                        DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
                        if ("Manual Input".equals(child.getUserObject())) {
                            manualNode = child;
                            break;
                        }
                    }
                    if (manualNode == null) {
                        manualNode = new DefaultMutableTreeNode("Manual Input");
                        root.add(manualNode);
                    }

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

        JCheckBox checkBox = new JCheckBox("Only Changes Since Common Ancestor (git diff <selection>..HEAD)");
        checkBox.setSelected(this.state.getTwoDotsCheckbox());
        checkBox.setBorder(JBUI.Borders.empty(1)); // top, left, bottom, right padding
        checkBox.addActionListener(e -> this.state.setTwoDotsCheckbox(checkBox.isSelected()));

        // Add a mouse listener to the help icon label to show the tool tip on hover
        checkBox.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent me) {
                checkBox.setToolTipText("See what you have added since [selection] branch point");
            }
        });

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
                //System.out.println(e);
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

}