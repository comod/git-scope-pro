package listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import service.ViewService;

import java.util.HashSet;
import java.util.Set;

public class MyChangeListListener implements ChangeListListener {

    private final ViewService viewService;
    private final Project myProject;
    private Set<Integer> previousChangeHashes = new HashSet<>();

    public MyChangeListListener(Project project) {
        this.myProject = project;
        this.viewService = project.getService(ViewService.class);
    }

    public void changeListUpdateDone() {

        ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

        // Use a hash set of integers for best performance
        Set<Integer> currentChangeHashes = new HashSet<>();

        for (Change change : changeListManager.getAllChanges()) {
            // Use file path (absolute or relative) + change type as hash key
            String filePath = getFilePath(change);
            String changeType = getChangeType(change);

            // Combine and compute a single hashCode (avoiding string concatenation)
            int hash = 31 * filePath.hashCode() + changeType.hashCode();
            currentChangeHashes.add(hash);
        }

        // Fast O(1) equality comparison
        boolean filesChanged = !currentChangeHashes.equals(previousChangeHashes);

        // Update for next trigger
        previousChangeHashes = currentChangeHashes;

        if (filesChanged) {
            // Only initiate the comparison if files have changed
            viewService.collectChanges(true);
        }
    }

    private String getFilePath(Change change) {
        // Use before or after revision; prefer after if available, else before
        if (change.getAfterRevision() != null) {
            return change.getAfterRevision().getFile().getPath();
        } else if (change.getBeforeRevision() != null) {
            return change.getBeforeRevision().getFile().getPath();
        } else {
            return "";
        }
    }

    private String getChangeType(Change change) {
        // You can use change.getType().name() for a stable identifier
        return change.getType().name();
    }
}
