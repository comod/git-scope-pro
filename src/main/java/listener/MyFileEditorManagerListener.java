package listener;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import example.ViewService;
import org.jetbrains.annotations.NotNull;

public class MyFileEditorManagerListener implements FileEditorManagerListener {

    private final ViewService viewService;

    public MyFileEditorManagerListener(Project project) {
        this.viewService = project.getService(ViewService.class);
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile vFile) {
        System.out.println("fileOpened doCompareAndUpdate");
        viewService.collectChanges();
    }
}
