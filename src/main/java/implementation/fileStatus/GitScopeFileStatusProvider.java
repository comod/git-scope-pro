package implementation.fileStatus;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.impl.FileStatusProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import service.ViewService;

import java.util.Map;

/**
 * Provides custom file status colors based on the active Git Scope tab
 * instead of the default diff against HEAD.
 *
 * Strategy:
 * 1. If file is in local changes towards HEAD → return null (let IntelliJ handle with gutter bars)
 * 2. If file is in Git Scope but NOT in local changes → return our custom status
 * 3. If file is not in scope → return null (let IntelliJ handle)
 *
 * This ensures that actively modified files get IntelliJ's default treatment (including gutter bars),
 * while historical Git Scope files get our custom colors.
 */
public class GitScopeFileStatusProvider implements FileStatusProvider {

    @Override
    public @Nullable FileStatus getFileStatus(@NotNull VirtualFile virtualFile) {
        // Get the project from the context
        Project project = getProjectFromFile(virtualFile);
        if (project == null || project.isDisposed()) {
            return null;
        }

        // Get the ViewService to access current scope's changes
        ViewService viewService = project.getService(ViewService.class);
        if (viewService == null) {
            return null;
        }

        String filePath = virtualFile.getPath();

        // STRATEGY: If file is in local changes towards HEAD, let IntelliJ handle it
        // This ensures gutter change bars work correctly for actively modified files
        // Use HashMap lookup for O(1) performance instead of iterating through all changes
        Map<String, Change> localChangesMap = viewService.getLocalChangesTowardsHeadMap();
        if (localChangesMap != null && localChangesMap.containsKey(filePath)) {
            // File is actively being modified - let IntelliJ's default provider handle it
            return null;
        }

        // File is NOT in local changes - check if it's in the Git Scope
        // Use HashMap lookup for O(1) performance instead of iterating through all changes
        Map<String, Change> scopeChangesMap = viewService.getCurrentScopeChangesMap();
        if (scopeChangesMap == null || scopeChangesMap.isEmpty()) {
            // No changes in scope - return null to fall back to default behavior
            return null;
        }

        // Check if this file has changes in the current scope using O(1) lookup
        Change change = scopeChangesMap.get(filePath);
        if (change != null) {
            // File is in Git Scope but NOT in local changes - we control the color
            // Use the FileStatus directly from the Change object
            return change.getFileStatus();
        }

        // File not in scope changes - return null to let default provider handle it
        return null;
    }

    /**
     * Helper to get project from VirtualFile context.
     * This is a workaround since FileStatusProvider doesn't pass project directly.
     */
    private @Nullable Project getProjectFromFile(@NotNull VirtualFile virtualFile) {
        // FileStatusProvider is project-specific, so we can use ProjectManager
        com.intellij.openapi.project.ProjectManager projectManager =
            com.intellij.openapi.project.ProjectManager.getInstance();

        for (com.intellij.openapi.project.Project project : projectManager.getOpenProjects()) {
            if (project.isDisposed()) {
                continue;
            }
            // Check if this file belongs to this project
            String basePath = project.getBasePath();
            if (basePath != null && virtualFile.getPath().startsWith(basePath)) {
                return project;
            }
        }
        return null;
    }

}
