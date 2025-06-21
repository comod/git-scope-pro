package toolwindow.elements;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class MySimpleChangesBrowser extends SimpleChangesBrowser {
    private final Project myProject;

    public void openAndScrollToChanges(Project project, VirtualFile file, int line) {
        FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
        for (FileEditor fileEditor : editors) {
            if (fileEditor instanceof TextEditor) {
                Editor editor = ((TextEditor) fileEditor).getEditor();

                // Move caret to the specific line if needed
                if (line > 0) {
                    LogicalPosition pos = new LogicalPosition(line - 1, 0);
                    editor.getCaretModel().moveToLogicalPosition(pos);
                }

                // Center the view on caret
                editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            }
        }
    }

    public MySimpleChangesBrowser(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
        super(project, changes);
        this.myProject = project;
    }

    @Override
    protected void onDoubleClick() {
        Change[] selectedChanges = getSelectedChanges().toArray(new Change[0]);
        if (selectedChanges.length > 0) {
            Change selectedChange = selectedChanges[0];
            VirtualFile file = selectedChange.getVirtualFile();
            if (file != null) {
                openAndScrollToChanges(myProject, file, -1); // or pass specific line number if known
            }
        }
    }


}
