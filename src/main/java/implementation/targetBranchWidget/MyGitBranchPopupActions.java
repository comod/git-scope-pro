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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchType;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Git;

import java.util.Collections;
import java.util.List;

import static git4idea.branch.GitBranchType.LOCAL;
import static implementation.targetBranchWidget.MyBranchActionGroupPopup._wrapWithMoreActionIfNeeded;
//import static implementation.targetBranchWidget.MyBranchActionGroupPopup._wrapWithMoreActionIfNeeded;
//import static implementation.targetBranchWidget.MyBranchActionGroupPopupOld._wrapWithMoreActionIfNeeded;

public class MyGitBranchPopupActions {

    private final Project project;
    private final GitRepository myRepository;

    public MyGitBranchPopupActions(Project project, GitRepository repository) {
        this.project = project;
        myRepository = repository;
    }

    public ActionGroup createActions() {
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
//        popupGroup.addAction(new GitBranchPopupActions.CheckoutRevisionActions("1"));
//        popupGroup.addAction(new CheckoutRevisionActions("2"));

        if (toInsert != null) {
            popupGroup.addAll(toInsert);
        }

        TargetBranchAction targetBranchActionHead = new TargetBranchAction(project, repositoryList, Git.BRANCH_HEAD, myRepository, LOCAL);
        popupGroup.add(targetBranchActionHead);

        popupGroup.addSeparator("Local Branches");
        GitLocalBranch currentBranch = myRepository.getCurrentBranch();
        GitBranchesCollection branchesCollection = myRepository.getBranches();

        List<TargetBranchAction> localBranchActions = getTargetBranchActions(repositoryList, currentBranch, branchesCollection);

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

        popupGroup.addSeparator("Remote Branches");

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

    @NotNull
    public List<TargetBranchAction> getTargetBranchActions(List<GitRepository> repositoryList, GitLocalBranch currentBranch, GitBranchesCollection branchesCollection) {
        return StreamEx.of(branchesCollection.getLocalBranches())
                .filter(branch -> !branch.equals(currentBranch))
                .map(branch -> new TargetBranchAction(project, repositoryList, branch.getName(), myRepository, LOCAL))
                .sorted((b1, b2) -> {
                    int delta = MyBranchActionUtil.FAVORITE_BRANCH_COMPARATOR.compare(b1, b2);
                    if (delta != 0) return delta;
                    return StringUtil.naturalCompare(b1.myBranchName, b2.myBranchName);
                })
                .toList();
    }

}
