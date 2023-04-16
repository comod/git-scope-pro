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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsMultiRootBranchConfig;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class MyDvcsBranchPopup<Repo extends Repository> {
    @NotNull
    protected final Project project;
    @NotNull
    protected final AbstractRepositoryManager<Repo> myRepositoryManager;
    @NotNull
    protected final DvcsSyncSettings myVcsSettings;
    @NotNull
    protected final AbstractVcs myVcs;
    @NotNull
    protected final DvcsMultiRootBranchConfig<Repo> myMultiRootBranchConfig;

    @NotNull
    protected final Repo myCurrentRepository;
    @NotNull
    protected final MyBranchActionGroupPopup myPopup;
    @NotNull
    protected final String myRepoTitleInfo;

    protected MyDvcsBranchPopup(@NotNull Repo currentRepository,
                                @NotNull AbstractRepositoryManager<Repo> repositoryManager,
                                @NotNull DvcsMultiRootBranchConfig<Repo> multiRootBranchConfig,
                                @NotNull DvcsSyncSettings vcsSettings,
                                @NotNull Condition<AnAction> preselectActionCondition, @Nullable String dimensionKey,
                                @NotNull DataContext dataContext
    ) {
        project = currentRepository.getProject();
        myCurrentRepository = currentRepository;
        myRepositoryManager = repositoryManager;
        myVcs = currentRepository.getVcs();
        myVcsSettings = vcsSettings;
        myMultiRootBranchConfig = multiRootBranchConfig;
        String title = "Target Branch";
        myRepoTitleInfo = (myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC)
                ? " in " + DvcsUtil.getShortRepositoryName(currentRepository) : "";
        myPopup = new MyBranchActionGroupPopup(
                title + myRepoTitleInfo,
                project,
                preselectActionCondition,
                createActions(),
                dimensionKey,
                dataContext
        );
        initBranchSyncPolicyIfNotInitialized();
        //    warnThatBranchesDivergedIfNeeded();
        if (myRepositoryManager.moreThanOneRoot()) {
            myPopup.addToolbarAction(new TrackReposSynchronouslyAction(myVcsSettings), true);
        }
    }

    @NotNull
    public ListPopup asListPopup() {
        return myPopup;
    }

    private void initBranchSyncPolicyIfNotInitialized() {
        if (myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.NOT_DECIDED) {
            if (myRepositoryManager.shouldProposeSyncControl()) {
                //        notifyAboutSyncedBranches();
                myVcsSettings.setSyncSetting(DvcsSyncSettings.Value.SYNC);
            } else {
                myVcsSettings.setSyncSetting(DvcsSyncSettings.Value.DONT_SYNC);
            }
        }
    }

    @NotNull
    private ActionGroup createActions() {
        LightActionGroup popupGroup = new LightActionGroup(false);
        AbstractRepositoryManager<Repo> repositoryManager = myRepositoryManager;
        if (repositoryManager.moreThanOneRoot()) {
            if (userWantsSyncControl()) {
                fillWithCommonRepositoryActions(popupGroup, repositoryManager);
            } else {
                fillPopupWithCurrentRepositoryActions(popupGroup, null);
            }
        } else {
            fillPopupWithCurrentRepositoryActions(popupGroup, null);
        }
        popupGroup.addSeparator();
        return popupGroup;
    }

    protected boolean userWantsSyncControl() {
        return (myVcsSettings.getSyncSetting() != DvcsSyncSettings.Value.DONT_SYNC);
    }

    protected abstract void fillWithCommonRepositoryActions(@NotNull LightActionGroup popupGroup,
                                                            @NotNull AbstractRepositoryManager<Repo> repositoryManager);

    @NotNull
    protected List<Repo> filterRepositoriesNotOnThisBranch(@NotNull final String branch,
                                                           @NotNull List<? extends Repo> allRepositories) {
        return ContainerUtil.filter(allRepositories, repository -> !branch.equals(repository.getCurrentBranchName()));
    }

    @NotNull
    protected abstract LightActionGroup createRepositoriesActions();

    protected abstract void fillPopupWithCurrentRepositoryActions(@NotNull LightActionGroup popupGroup,
                                                                  @Nullable LightActionGroup actions);

    private static class TrackReposSynchronouslyAction extends ToggleAction implements DumbAware {
        private final DvcsSyncSettings myVcsSettings;

        TrackReposSynchronouslyAction(@NotNull DvcsSyncSettings vcsSettings) {
            super(DvcsBundle.message("sync.setting"), DvcsBundle.message("sync.setting.description", "repository"), null);
            myVcsSettings = vcsSettings;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            myVcsSettings.setSyncSetting(state ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
        }
    }
}
