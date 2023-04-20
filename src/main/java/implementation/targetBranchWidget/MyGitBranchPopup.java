package implementation.targetBranchWidget;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.popup.list.ListPopupImpl;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import implementation.Manager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import service.GitService;
import ui.StatusBar;

import java.util.List;

import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.retainAll;
import static git4idea.GitUtil.getRepositoryManager;

public class MyGitBranchPopup extends MyDvcsBranchPopup<GitRepository> {

    static final String SHOW_ALL_LOCALS_KEY = "Git.Branch.Popup.ShowAllLocals";
    static final String SHOW_ALL_REMOTES_KEY = "Git.Branch.Popup.ShowAllRemotes";
    static final String SHOW_ALL_REPOSITORIES = "Git.Branch.Popup.ShowAllRepositories";
    private static final String DIMENSION_SERVICE_KEY = "Git.Branch.Popup";
    private static Manager manager;

    private MyGitBranchPopup(
            @NotNull final Project project,
            @NotNull GitRepository currentRepository,
            @NotNull GitRepositoryManager repositoryManager,
            @NotNull GitVcsSettings vcsSettings,
            @NotNull Condition<AnAction> preselectActionCondition,
            @NotNull DataContext dataContext
    ) {

        super(
                // currentRepository,
                (project.getService(GitService.class)).getRepository(),
                repositoryManager,
                new GitMultiRootBranchConfig(repositoryManager.getRepositories()),
                vcsSettings,
                preselectActionCondition,
                DIMENSION_SERVICE_KEY,
                dataContext
        );

    }

    /**
     * @param currentRepository Current repository, which means the repository of the currently open or selected file.
     *                          In the case of synchronized branch operations current repository matter much less, but sometimes is used,
     *                          for example, it is preselected in the repositories combobox in the compare branches dialog.
     */
    public static MyGitBranchPopup getInstance(
            @NotNull final Project project,
            @NotNull GitRepository currentRepository,
            @NotNull DataContext dataContext
    ) {
        manager = project.getService(Manager.class);
        final GitVcsSettings vcsSettings = GitVcsSettings.getInstance(project);
        Condition<AnAction> preselectActionCondition = action -> false;

        return new MyGitBranchPopup(
                project,
                currentRepository,
                getRepositoryManager(project),
                vcsSettings,
                preselectActionCondition,
                dataContext
        );
    }

    @Override
    protected void fillWithCommonRepositoryActions(
            @NotNull LightActionGroup popupGroup,
            @NotNull AbstractRepositoryManager<GitRepository> repositoryManager
    ) {

//        this.fillPopupWithCurrentRepositoryActions(popupGroup, null);
        popupGroup.addAll(createRepositoriesActions());

    }

    @NotNull
    @Override
    protected LightActionGroup createRepositoriesActions() {

        LightActionGroup popupGroup = new LightActionGroup(false);
        popupGroup.addSeparator("Repositories");
        List<ActionGroup> rootActions = map(
                DvcsUtil.sortRepositories(myRepositoryManager.getRepositories()),
                repo -> {
                    return new RootAction<>(
                            repo,
                            new MyGitBranchPopupActions(repo.getProject(), repo).createActions(),
                            null
//                            manager.getTargetBranchByRepositoryDisplay(repo)
                    );
                }
        );

        popupGroup.addAll(rootActions);
//        wrapWithMoreActionIfNeeded(
//            project,
//            popupGroup,
//            rootActions,
//            rootActions.size() > MAX_NUM ? DEFAULT_NUM : MAX_NUM,
//            SHOW_ALL_REPOSITORIES
//        );

        return popupGroup;
    }

    @Override
    protected void fillPopupWithCurrentRepositoryActions(@NotNull LightActionGroup popupGroup, @Nullable LightActionGroup actions) {
        popupGroup.addAll(
                new MyGitBranchPopupActions(
                        myCurrentRepository.getProject(),
                        myCurrentRepository
                ).createActions(
                        null,
                        myRepoTitleInfo,
                        true
                )
        );
    }

    public void setPopupLastOpenedAtList() {
        ListPopup popup = asListPopup();
        ListPopupImpl popupimpl = (ListPopupImpl) popup;
        setPopupLastOpenedAt(popupimpl.getList());
    }

    public void setPopupLastOpenedAt(Object lastOpenedAt) {
        manager.setLastOpenedAt(lastOpenedAt);
    }

    public Object getLastOpenedAt() {
        return manager.getLastOpenedAt();
    }
}