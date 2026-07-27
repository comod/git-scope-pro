package listener;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;
import service.ViewService;
import system.Defs;

/**
 * Listens to Git repository changes (branches, tags, HEAD changes, remote updates).
 * This complements MyChangeListListener which only triggers on working tree changes.
 *
 * This listener is essential for detecting:
 * - New tags being created
 * - New branches being created/deleted
 * - Branch checkouts
 * - Remote reference updates (fetch/pull)
 */
public class MyGitRepositoryChangeListener implements GitRepositoryChangeListener {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(MyGitRepositoryChangeListener.class);

    private final ViewService viewService;

    public MyGitRepositoryChangeListener(Project project) {
        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void repositoryChanged(@NotNull GitRepository repository) {
        LOG.debug("repositoryChanged() called for repository: " + repository.getRoot().getName());

        // TODO: collectChanges: repository changed (branches, tags, HEAD, remotes updated)
        viewService.collectChanges(true);
    }
}
