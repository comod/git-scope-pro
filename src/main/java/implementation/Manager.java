package implementation;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
//import com.intellij.openapi.ui.popup.JBPopup;
//import com.intellij.openapi.ui.popup.JBPopupFactory;
//import java.awt.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import service.GitService;
import git4idea.repo.GitRepository;
import implementation.compare.ChangesService;
import implementation.lineStatusTracker.MyLineStatusTrackerImpl;
import implementation.scope.MyScope;
import org.jetbrains.annotations.NotNull;
import ui.ToolWindowUI;

import javax.swing.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Thread.sleep;
import static java.util.stream.Collectors.joining;

public class Manager {

    private static boolean busy;
    private final Map<GitRepository, Collection<Change>> changesByRepoMap = new HashMap<>();
    private ToolWindowUI toolWindowUI;
    private MyScope myScope;
    private MyLineStatusTrackerImpl myLineStatusTrackerImpl;
    private Project project;

    //    Map<String, String> repositoryTargetBranchMap = null;
//    private Git git;
    private boolean initialized = false;
    private Collection<Change> changes;
    private Collection<Change> changesBefore;
    private Queue<String> queue;
    private ChangesService myGitCompareWithBranchAction;
    private MessageBus messageBus;
    private MessageBusConnection messageBusConnection;

    private Object lastOpenedAt;
    private GitService git;
//    private InitialUpdate initialUpdate;

    public Manager() {
        // Keep Constructor empty, because this is a Service
    }

    public void init(Project project) {

        System.out.println("init");

        this.project = project;
        this.git = project.getService(GitService.class);
//        this.git = new Git(project);
//        ToolWindowUI toolWindowUI = new ToolWindowUI(
//                project,
//                getGit(),
//                this
//        );
//        setToolWindowUI(toolWindowUI);
//        this.targetBranch = new TargetBranch(project, state, git, toolWindowUI);

        // Scope
        this.myScope = new MyScope(project);

        // LST
//        this.myLineStatusTrackerImpl = new MyLineStatusTrackerImpl(project);
//
//        this.initQueue();
//
        this.messageBus = this.project.getMessageBus();
        this.messageBusConnection = messageBus.connect();

        // Init / First compare on init
//        messageBusConnection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, () -> {
        onInit();
//        });

        // Listener
//        this.editorListener();

    }


    public ToolWindowUI getToolWindowUI() {
        return toolWindowUI;
    }

    public void setToolWindowUI(ToolWindowUI toolWindowUI) {
        this.toolWindowUI = toolWindowUI;
    }

    public void onInit() {

        // Do Initial Compare and Update
//        System.out.println("OnInit");

//        this.targetBranch.setFeatureActive(true);
//        ToolWindowUI toolWindowUI = this.getToolWindowUI();
//        boolean toolWindowWasOpen = toolWindowUI != null;
//        if (toolWindowWasOpen) {
//            return;
//        }

//        SwingUtilities.invokeLater(this::doCompareAndUpdate);
        initialUpdate();

    }

    public void initialUpdate() {
        if (initialized) {
            return;
        }
//        initialUpdate.start();
        SwingUtilities.invokeLater(() -> {
            setInitialized(true);
            doCompareAndUpdate();
        });
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    private void editorListener() {


        //        messageBusConnection.subscribe(ChangeListManagerImpl.LISTS_LOADED, lists -> {
        //            if (lists.isEmpty()) return;
        //            try {
        //                //                doCompareAndUpdate();
        //                // ChangeListManager.getInstance(project).setReadOnly(LocalChangeList.DEFAULT_NAME, true);
        //
        //                //                if (!myConfiguration.changeListsSynchronized()) {
        //                //                    processChangeLists(lists);
        //                //                }
        //            } catch (ProcessCanceledException e) {
        //                //
        //            }
        //
        //        });


        messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile vFile) {
//                    System.out.println("fileOpened doCompareAndUpdate");
                        doCompareAndUpdate();
                    }

                    @Override
                    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                        //                    System.out.println("2");
                        //                    doCompareAndUpdate();
                    }
                }
        );

//                VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
//                    @Override
//                    public void contentsChanged(@NotNull VirtualFileEvent event) {
//                        System.out.println("fileStatusChangedVirtualFile");
//
//                    }
//                });

//        FileStatusManager.getInstance(this.project).addFileStatusListener(new FileStatusListener() {
//            //            @Override
//            //            public void fileStatusesChanged() {
//            //                System.out.println("fileStatusesChanged");
//            //                doCompareAndUpdate();
//            //            }
//
//            @Override
//            public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
////                System.out.println("fileStatusChangedVirtualFile");
//                doCompareAndUpdate(virtualFile);
//            }
//        }, () -> {
//        });

    }
//
//    public void initOnOpenToolWindow() {
//        targetBranch.initOnOpenToolWindow();
//    }
//
//    public String getTargetBranchDisplay() {
//        return targetBranch.getTargetBranchDisplay();
//    }
//
//    public String getTargetBranchByRepository(GitRepository repo) {
//        return targetBranch.getTargetBranchByRepository(repo);
//    }
//
//    public String getTargetBranchByRepositoryDisplay(GitRepository repo) {
//        return targetBranch.getTargetBranchByRepositoryDisplay(repo);
//    }
//
//    public void doCompareAndUpdate(@NotNull VirtualFile virtualFile) {
//
//        if (!this.initialized) {
//            return;
//        }
//
//        if (!this.targetBranch.isFeatureActive()) {
//            // updateFromHEAD(virtualFile); //@todo
//            updateFromHEAD();
//            return;
//        }
//
//    }

    public void doCompareAndUpdate() {

        if (!this.initialized) {
            return;
        }

        updateFromHEAD();
        return;
//        if (!this.targetBranch.isFeatureActive()) {
//            updateFromHEAD();
//            return;
//        }

//        queue.add(""); // No parameter needed

    }

    public Object getLastOpenedAt() {
        return this.lastOpenedAt;
    }

    public void setLastOpenedAt(Object lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    public void onJobDone(GitRepository repo, Collection<Change> changes) {

        // System.out.println("done!");
        changesByRepoMap.put(repo, changes);
        update();

    }

    public void updateFromHEAD() {

//        System.out.println("updateFromHEAD");
        changes = this.getOnlyLocalChanges();
        onAfterUpdate();

    }

    public void update() {

        changes = null;

        git.getRepositories().forEach(repo -> {

            Collection<Change> changesByRepo = changesByRepoMap.get(repo);

            if (changesByRepo == null) {
                return;
            }

            if (changes == null) {
                changes = changesByRepo;
                return;
            }

            Stream<Change> combinedStream = Stream.of(changes, changesByRepo).flatMap(Collection::stream);
            changes = combinedStream.collect(Collectors.toList());

        });

//        changes = addLocalChanges(changes);

        if (changes == null) {
            return;
        }

        //        if (toolWindowUI != null) {
        //            toolWindowUI.setLoadingStatus(false);
        //        }


        onAfterUpdate();
    }

    //    public void updateFromHEAD(@NotNull VirtualFile virtualFile) {
    //
    //        changes = this.getOnlyLocalChanges();
    //        onAfterUpdate(virtualFile);
    //
    //    }

    public void onAfterUpdate() {

//        System.out.println("+++ onAfterUpdate +++");

        boolean changesAreTheSame = false;
        if (changesBefore != null && changesBefore.equals(changes)) {
            changesAreTheSame = true;
        }

        changesBefore = changes;

//        this.updateToolWindowUI();
//
////        if (!changesAreTheSame) {
//        this.updateDiff();
////        }
//        this.updateLst();
//        this.updateScope();

        // PUBLISH to custom listener
//        ChangeActionNotifierInterface publisher = messageBus.syncPublisher(ChangeActionNotifierInterface.CHANGE_ACTION_TOPIC);
//        publisher.doAction("doAction");

    }

    public void onAfterUpdate(@NotNull VirtualFile virtualFile) {

        this.updateToolWindowUI();
        this.updateDiff();
        this.updateLst(virtualFile);
        this.updateScope();

        // PUBLISH to custom listener
//        ChangeActionNotifierInterface publisher = messageBus.syncPublisher(ChangeActionNotifierInterface.CHANGE_ACTION_TOPIC);
//        publisher.doAction("doAction");

    }

    public void updateToolWindowUI() {

        ToolWindowUI toolWindowUI = this.getToolWindowUI();
        if (toolWindowUI != null) {
            toolWindowUI.update();
        }

    }

    public void updateDiff() {

        if (this.toolWindowUI == null) {
            return;
        }

        this.toolWindowUI.updateVcsTree(this.changes);

    }

    public void updateLst() {
        this.myLineStatusTrackerImpl.update(this.changes, null);
    }

    public void updateLst(@NotNull VirtualFile virtualFile) {

        // @todo
        // Boolean isFeatureActive = !this.targetBranch.isFeatureActive();
        this.myLineStatusTrackerImpl.update(this.changes, virtualFile);

    }

    public void updateScope() {
        this.myScope.update(this.changes);
    }

    public Collection<Change> getOnlyLocalChanges() {

//        Collection<Change> changes = new ArrayList<>();

        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        Collection<Change> localChanges = changeListManager.getAllChanges();

//        for (Change localChange : localChanges) {
//
//            VirtualFile localChangeVirtualFile = localChange.getVirtualFile();
//            if (localChangeVirtualFile == null) {
//                continue;
//            }
//
//            changes.add(localChange);
//
//        }

        return localChanges;
    }

    public Collection<Change> addLocalChanges(Collection<Change> changes) {

//        if (changes == null) {
//            return;
//        }

        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        Collection<Change> localChanges = changeListManager.getAllChanges();

        for (Change localChange : localChanges) {

            VirtualFile localChangeVirtualFile = localChange.getVirtualFile();
            if (localChangeVirtualFile == null) {
                continue;
            }
            String localChangePath = localChangeVirtualFile.getPath();

//            System.out.println("=================================================================");
//            System.out.println(localChangePath);
//
//            String repoPath = repo.toString();
//            System.out.println(repoPath);
//
//            if (!isLocalChangeForThisRepo(localChangePath)) {
//                System.out.println("not for this Repo - Skip!");
//                continue;
//            }

            if (isLocalChangeOnly(localChangePath, changes)) {
                changes.add(localChange);
            }

        }

        return changes;
    }

    private Boolean isLocalChangeOnly(String localChangePath, Collection<Change> changes) {

        if (changes == null) {
            return false;
        }

        for (Change change : changes) {
            VirtualFile vFile = change.getVirtualFile();
            if (vFile == null) {
                return false;
            }
            String changePath = change.getVirtualFile().getPath();

            if (localChangePath.equals(changePath)) {
                // we have already this file in our changes-list
                return false;
            }
        }

        return true;

    }
//
//    private Boolean isLocalChangeForThisRepo(String localChangePath) {
//        GitRepository repo = MyGitCompareWithBranchAction.this.repo;
//        String repoPath = repo.toString();
//        return localChangePath.contains(repoPath);
//    }

    public void toggleHeadAction() {

//        this.targetBranch.toggleFeature();

//        System.out.println("doCompareAndUpdate toggleHeadAction");
        this.doCompareAndUpdate();

    }
}
