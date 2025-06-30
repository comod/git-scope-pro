
package toolwindow.elements;

import com.intellij.openapi.project.Project;
import service.GitService;

import javax.swing.*;

public class CurrentBranch extends JLabel {

    public static final int MAX_BRANCH_COUNT = 2;
    private final GitService git;

    public CurrentBranch(Project project) {
        this.git = project.getService(GitService.class);
        this.createElement();
        this.addListener();
    }

    public void createElement() {
        // Set up UI properties first
        setSize(30, 3);
        setVerticalAlignment(JLabel.CENTER);
        setHorizontalAlignment(JLabel.CENTER);
        setVerticalTextPosition(JLabel.CENTER);
        setHorizontalTextPosition(JLabel.CENTER);
        setAlignmentX(JLabel.CENTER);

        // Set initial placeholder text while loading
        setText("Loading...");

        // Asynchronously load branch information
        update();
    }

    public void addListener() {
        // Implementation for listener if needed
    }

    public void update() {
        // Use the asynchronous method instead of the synchronous one
        git.getRepositoriesAsync(repositories -> {
            int count = repositories.size();

            // Use the async version to get branch name
            git.getCurrentBranchNameAsync(branchName -> {
                if (count >= MAX_BRANCH_COUNT) {
                    branchName = count + " Repositories";
                }
                setText(branchName);
                setToolTipText(branchName);
            });
        });
    }
}