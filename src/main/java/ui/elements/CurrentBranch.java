package ui.elements;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import service.GitService;
import utils.Git;

import javax.swing.*;
import java.awt.*;

public class CurrentBranch extends JLabel {

    public static final int MAX_BRANCH_COUNT = 2;
    private final GitService git;

    public CurrentBranch(Project project) {

        this.git = project.getService(GitService.class);

        this.createElement();
        this.addListener();

    }

    public void createElement() {
        update();
    }

    public void addListener() {

    }

    public void update() {
        setSize(30, 3);
        setVerticalAlignment(JLabel.CENTER);
        setHorizontalAlignment(JLabel.CENTER);
        setVerticalTextPosition(JLabel.CENTER);
        setHorizontalTextPosition(JLabel.CENTER);
        setAlignmentX(JLabel.CENTER);
        String currentBranchName = git.getCurrentBranchName();
        String branchName = currentBranchName;
        int count = git.getRepositories().size();

        if (count >= MAX_BRANCH_COUNT) {
            branchName = count + " Repositories";
            setToolTipText(currentBranchName);
        }

        setText(branchName);

    }

}
