package service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.repo.GitRepository;
import implementation.compare.MyGitCompareWithBranchAction;
import implementation.lineStatusTracker.MyLineStatusTrackerImpl;
import implementation.scope.MyScope;
import model.valueObject.TargetBranch;
import ui.ToolWindowUI;

import java.util.*;
import java.util.function.Consumer;

public class ChangesService {

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
    private MyGitCompareWithBranchAction myGitCompareWithBranchAction;
    private MessageBus messageBus;
    private MessageBusConnection messageBusConnection;

    private Object lastOpenedAt;
    private GitService git;

    public ChangesService(Project project) {
        this.project = project;
        this.git = project.getService(GitService.class);
        this.myGitCompareWithBranchAction = new MyGitCompareWithBranchAction();
    }

    public void initQueue() {
//        queue = new LinkedList<>();
//        QueueWorker queueWorker = new QueueWorker(queue);
//        queueWorker.start();
    }

//    public void update() {
//
//        changes = null;
//
//        git.getRepositories().forEach(repo -> {
//
//            Collection<Change> changesByRepo = changesByRepoMap.get(repo);
//
//            if (changesByRepo == null) {
//                return;
//            }
//
//            if (changes == null) {
//                changes = changesByRepo;
//                return;
//            }
//
//            Stream<Change> combinedStream = Stream.of(changes, changesByRepo).flatMap(Collection::stream);
//            changes = combinedStream.collect(Collectors.toList());
//
//        });
//
//    }

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


    public void collectChanges(TargetBranch targetBranchByRepo, Consumer<Collection<Change>> callBack) {
        git.getRepositories().forEach(repo -> {
            String branchToCompare = targetBranchByRepo.getValue().get(repo.toString());
//            String targetBranchByRepo = getTargetBranchByRepository(repo);
//            if (targetBranchByRepo == null) {
//                // Notification.notify(Defs.NAME, "Choose a Branch");
//                toolWindowUI.showTargetBranchPopupAtToolWindow();
//                targetBranchByRepo = Git.BRANCH_HEAD;
//            }

            myGitCompareWithBranchAction.collectChangesAndProcess(project, repo, branchToCompare, callBack);
        });
    }

}
