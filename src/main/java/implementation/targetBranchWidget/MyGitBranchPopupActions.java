/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package implementation.targetBranchWidget;

import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import example.ViewService;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchType;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchManager;
import implementation.Manager;
import model.valueObject.TargetBranch;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Git;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static git4idea.branch.GitBranchType.LOCAL;
import static implementation.targetBranchWidget.MyBranchActionGroupPopup._wrapWithMoreActionIfNeeded;
//import static implementation.targetBranchWidget.MyBranchActionGroupPopupOld._wrapWithMoreActionIfNeeded;

class MyGitBranchPopupActions {

    private final Project project;
    private final GitRepository myRepository;

    MyGitBranchPopupActions(Project project, GitRepository repository) {
        this.project = project;
        myRepository = repository;
    }

    ActionGroup createActions() {
        return createActions(null, "", true);
    }

    ActionGroup createActions(@Nullable LightActionGroup toInsert, @NotNull String repoInfo, boolean firstLevelGroup) {
        LightActionGroup popupGroup = new LightActionGroup(false);
        List<GitRepository> repositoryList = Collections.singletonList(myRepository);

//    GitRebaseSpec rebaseSpec = GitRepositoryManager.getInstance(project).getOngoingRebaseSpec();
//    if (rebaseSpec != null && isSpecForRepo(rebaseSpec, myRepository)) {
//      popupGroup.addAll(getRebaseActions());
//    }
//    else {
//      popupGroup.addAll(createPerRepoRebaseActions(myRepository));
//    }

//    popupGroup.addAction(new createBranch(project, repositoryList));
//    popupGroup.addAction(new CheckoutRevisionActions("1"));
//    popupGroup.addAction(new CheckoutRevisionActions("2"));

        if (toInsert != null) {
            popupGroup.addAll(toInsert);
        }

        TargetBranchAction targetBranchActionHead = new TargetBranchAction(project, repositoryList, Git.BRANCH_HEAD, myRepository, LOCAL);
        popupGroup.add(targetBranchActionHead);

        popupGroup.addSeparator("Local Branches" + repoInfo);
        GitLocalBranch currentBranch = myRepository.getCurrentBranch();
        GitBranchesCollection branchesCollection = myRepository.getBranches();

        List<TargetBranchAction> localBranchActions = StreamEx.of(branchesCollection.getLocalBranches())
                .filter(branch -> !branch.equals(currentBranch))
                .map(branch -> {
                    TargetBranchAction targetBranchAction = new TargetBranchAction(project, repositoryList, branch.getName(), myRepository, LOCAL);
                    return targetBranchAction;
                })
                .sorted((b1, b2) -> {
                    int delta = MyBranchActionUtil.FAVORITE_BRANCH_COMPARATOR.compare(b1, b2);
                    if (delta != 0) return delta;
                    return StringUtil.naturalCompare(b1.myBranchName, b2.myBranchName);
                })
                .toList();

        int topShownBranches = MyBranchActionUtil.getNumOfTopShownBranches(localBranchActions);

        if (currentBranch != null) {
            localBranchActions.add(
                    0,
                    new TargetBranchAction(project, repositoryList, currentBranch.getName(), myRepository, LOCAL)
            );
            topShownBranches++;
        }

        // if there are only a few local favorites -> show all;  for remotes it's better to show only favorites;
        _wrapWithMoreActionIfNeeded(
                project,
                popupGroup,
                localBranchActions,
                topShownBranches,
                firstLevelGroup ? MyGitBranchPopup.SHOW_ALL_LOCALS_KEY : null,
                firstLevelGroup
        );

//    popupGroup.addAll(localBranchActions);

        popupGroup.addSeparator("Remote Branches" + repoInfo);

        List<TargetBranchAction> remoteBranchActions = StreamEx.of(branchesCollection.getRemoteBranches())
                .map(GitBranch::getName)
                .sorted(StringUtil::naturalCompare)
                .map(remoteName -> {
                    return new TargetBranchAction(project, repositoryList, remoteName, myRepository, GitBranchType.REMOTE);
//               return new LocalBranchActions.RemoteBranchActions(project, repositoryList, remoteName, myRepository);
                })
                .toList();

        _wrapWithMoreActionIfNeeded(
                project,
                popupGroup,
                remoteBranchActions,
                MyBranchActionUtil.getNumOfTopShownBranches(remoteBranchActions),
                firstLevelGroup ? MyGitBranchPopup.SHOW_ALL_REMOTES_KEY : null,
                firstLevelGroup
        );


//        List<LocalBranchActions.RemoteBranchActions> remoteBranchActions = StreamEx.of(branchesCollection.getRemoteBranches())
//                .map(GitBranch::getName)
//                .sorted(StringUtil::naturalCompare)
//                .map(remoteName -> new LocalBranchActions.RemoteBranchActions(project, repositoryList, remoteName, myRepository))
//                .toList();
//        wrapWithMoreActionIfNeeded(project, popupGroup, sorted(remoteBranchActions, BranchActionUtil.FAVORITE_BRANCH_COMPARATOR),
//                getNumOfTopShownBranches(remoteBranchActions), firstLevelGroup ? MyGitBranchPopup.SHOW_ALL_REMOTES_KEY : null);

        // @todo Remember Feature
        //    popupGroup.addSeparator("Options");
        //    TargetBranchAction targetBranchActionRemember = new TargetBranchAction(project, repositoryList, "Remember", myRepository, LOCAL);
        //    popupGroup.add(targetBranchActionRemember);

        return popupGroup;
    }

    public static class TargetBranchAction extends MyBranchAction {

        private final GitBranchManager gitBranchManager;
        private final ImmutableList<? extends GitRepository> myRepositories;
        private final GitRepository mySelectedRepository;
        private final Project project;
        private Boolean hide = false;

        TargetBranchAction(
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
            ViewService viewService = project.getService(ViewService.class);
//            MyBranchAction myBranchAction = (MyBranchAction) e;
            Map<String, String> repositoryTargetBranchMap = new HashMap<>();
            repositoryTargetBranchMap.put(myBranchAction.getRepoName(), myBranchAction.getBranchName());
            viewService.getCurrent().setTargetBranch(new TargetBranch(repositoryTargetBranchMap));
        }

        public Boolean getHide() {
            return hide;
        }

        public void setHide(Boolean hide) {
            this.hide = hide;
        }

    }
}
