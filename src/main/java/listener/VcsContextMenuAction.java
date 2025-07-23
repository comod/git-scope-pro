package listener;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.history.actions.CopyRevisionNumberAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vcs.VcsDataKeys;
import service.ViewService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class VcsContextMenuAction extends CopyRevisionNumberAction {

    @NotNull
    private static List<VcsRevisionNumber> getRevisionNumbersFromContext(@NotNull AnActionEvent e) {
        VcsRevisionNumber[] revisionNumbers = e.getData(VcsDataKeys.VCS_REVISION_NUMBERS);

        return revisionNumbers != null ? Arrays.asList(revisionNumbers) : Collections.emptyList();
    }

    @NotNull
    private static String getHashesAsString(@NotNull List<? extends VcsRevisionNumber> revisions) {
        return StringUtil.join(revisions, VcsRevisionNumber::asString, " ");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<VcsRevisionNumber> revisions = getRevisionNumbersFromContext(e);
        revisions = ContainerUtil.reverse(revisions); // we want hashes from old to new, e.g. to be able to pass to native client in terminal
        String rev = getHashesAsString(revisions);

        Project project = e.getProject();
        ViewService viewService = Objects.requireNonNull(project).getService(ViewService.class);
        viewService.addRevisionTab(rev);
    }
}
