package ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.ObjectUtils;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.ui.branch.GitBranchWidget;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import implementation.targetBranchWidget.MyDvcsStatusWidget;
import implementation.targetBranchWidget.MyGitBranchPopup;

import java.util.Objects;

public class StatusBar extends MyDvcsStatusWidget<GitRepository> {

    public static final String WIDGET_ID = "TargetBranchStatusBarWidget";

    private final GitVcsSettings mySettings;

    @NotNull
    @Override
    public String ID() {
        return WIDGET_ID;
    }

    public StatusBar(@NotNull Project project) {
        super(project, "");
        mySettings = GitVcsSettings.getInstance(project);
    }

    @Override
    public StatusBarWidget copy() {
        return new GitBranchWidget(Objects.requireNonNull(getProject()));
    }

    @Nullable
    @Override
    @CalledInAwt
    protected GitRepository guessCurrentRepository(@NotNull Project project) {
        return DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
    }

//    @NotNull
//    @Override
//    protected ListPopup getPopup(@NotNull Project project, @NotNull GitRepository repository) {
//
////        DataManager.getInstance().getDataContext(this.getComponent()));
//        MyGitBranchPopup myGitBranchPopup = MyGitBranchPopup.getInstance(project, repository);
//        myGitBranchPopup.setPopupLastOpenedAtList();
//        return myGitBranchPopup.asListPopup();
//    }

    @NotNull
    @Override
    protected ListPopup getPopup(@NotNull Project project, @NotNull GitRepository repository) {
        return MyGitBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(myStatusBar.getComponent()))
                .asListPopup();
    }

    @Override
    protected void subscribeToRepoChangeEvents(@NotNull Project project) {
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
            @Override
            public void repositoryChanged(@NotNull GitRepository repository) {
//                LOG.debug("repository changed");
                updateLater();
            }
        });
    }

    @Override
    protected void rememberRecentRoot(@NotNull String path) {
        mySettings.setRecentRoot(path);
    }

}
