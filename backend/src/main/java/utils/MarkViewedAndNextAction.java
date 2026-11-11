package utils;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextFileIterable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import service.ReviewedFilesService;
import service.ViewService;
import state.State;

/**
 * Diff toolbar button: marks the file shown in the diff as viewed, then moves to the next file of
 * the diff chain. On the last file the mark is set and the diff stays.
 */
public final class MarkViewedAndNextAction extends DumbAwareAction {

    private final Change change;

    public MarkViewedAndNextAction(@NotNull Change change) {
        super("Mark as Viewed and Go to Next File", "Mark this file as viewed and open the next file",
                AllIcons.Actions.Checked);
        this.change = change;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null
                && project.getService(State.class).getReviewModeEnabled());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        ReviewedFilesService reviewed = project.getService(ReviewedFilesService.class);
        MyModel model = project.getService(ViewService.class).getCurrent();
        reviewed.setReviewed(model, change, true);
        reviewed.refreshTree();

        PrevNextFileIterable files = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE);
        if (files != null && files.canGoNext(false)) files.goNext(false);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
