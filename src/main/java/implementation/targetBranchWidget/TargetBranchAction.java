package implementation.targetBranchWidget;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import example.ViewService;
import git4idea.branch.GitBranchType;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static git4idea.branch.GitBranchType.LOCAL;

public class TargetBranchAction extends MyBranchAction {

    private final GitBranchManager gitBranchManager;
    private final ImmutableList<? extends GitRepository> myRepositories;
    private final GitRepository mySelectedRepository;
    private final Project project;
    private Boolean hide = false;

    public TargetBranchAction(
            @NotNull Project project,
            @NotNull List<? extends GitRepository> repositories,
            @NotNull String branchName,
            @NotNull GitRepository repo,
            GitBranchType gitBranchType
    ) {
        super(repo, branchName);

        this.project = project;
        myRepositories = ContainerUtil.immutableList(repositories);
        //    myBranchName = branchName;
        mySelectedRepository = repo;
        gitBranchManager = project.getService(GitBranchManager.class);

        setFavorite(gitBranchManager.isFavorite(gitBranchType, repositories.size() > 1 ? null : mySelectedRepository, myBranchName));

    }

    @Nullable
    private GitRepository chooseRepo() {
        return myRepositories.size() > 1 ? null : mySelectedRepository;
    }

    @Override
    public void toggle() {
        super.toggle();
        gitBranchManager.setFavorite(LOCAL, chooseRepo(), myBranchName, isFavorite());
    }

    public void update(@NotNull AnActionEvent e) {

    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        //      try {
        //        Thread.sleep(200);
        //      } catch (InterruptedException ex) {
        //        ex.printStackTrace();
        //      }


//            Manager manager = project.getService(Manager.class);
//            manager.targetBranchListener(this);
        targetBranchListener(this);
    }

    public void targetBranchListener(MyBranchAction myBranchAction) {
        System.out.println("targetBranchListener");
        ViewService viewService = project.getService(ViewService.class);
//            MyBranchAction myBranchAction = (MyBranchAction) e;
        Map<String, String> repositoryTargetBranchMap = new HashMap<>();
        repositoryTargetBranchMap.put(myBranchAction.getRepoName(), myBranchAction.getBranchName());
        // @todo delete reverse from here
//        viewService.getCurrent().setTargetBranch(new TargetBranch(repositoryTargetBranchMap));
    }

    public Boolean getHide() {
        return hide;
    }

    public void setHide(Boolean hide) {
        this.hide = hide;
    }

}
