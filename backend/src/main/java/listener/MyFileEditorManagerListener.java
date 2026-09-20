package listener;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import service.ViewService;
import org.jetbrains.annotations.NotNull;

public class MyFileEditorManagerListener implements FileEditorManagerListener {

    private final ViewService viewService;

    public MyFileEditorManagerListener(Project project) {
        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile vFile) {
        /* Opening a file changes no scope input, so this deliberately does NOT bump the apply
         * generation: doing so discarded the result of any in-flight fresh collection (e.g. one
         * triggered by a just-finished rebase) and replaced it with this cache-served one --
         * the scope then showed pre-operation state, stuck until the next tab switch (issue #78).
         * This call only warms the model when nothing has been collected yet. */
        viewService.collectChanges(false);
    }
}
