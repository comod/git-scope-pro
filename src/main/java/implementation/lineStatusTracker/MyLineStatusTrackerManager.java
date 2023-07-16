package implementation.lineStatusTracker;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class MyLineStatusTrackerManager {

    @NotNull
    private final SimpleLineStatusTracker myLineStatusTracker;

    MyLineStatusTrackerManager(Project project, Document document) {
        myLineStatusTracker = new SimpleLineStatusTracker(project, document, MyLineStatusMarkerRenderer::new);
    }

    void setBaseRevision(CharSequence vcsContent) {
//        SwingUtilities.invokeLater(() -> {
//            //System.out.println("setBaseRevision!");
        if (vcsContent == null) {

            //System.out.println("vcsContent == null");
            return;
        }
        try {
            this.myLineStatusTracker.setBaseRevision(vcsContent);
        } catch (Exception e) {
            //System.out.println("avoid exception be thrown!");
        }
//        });
    }

    void release() {
        this.myLineStatusTracker.release();
    }

    private static class MyLineStatusMarkerRenderer extends LineStatusMarkerPopupRenderer {
        MyLineStatusMarkerRenderer(@NotNull LineStatusTrackerI tracker) {
            super(tracker);
        }

        @NotNull
        @Override
        protected List<AnAction> createToolbarActions(@NotNull Editor editor, @NotNull Range range, @Nullable Point mousePosition) {
            List<AnAction> actions = new ArrayList<>();
            actions.add(new ShowPrevChangeMarkerAction(editor, range));
            actions.add(new ShowNextChangeMarkerAction(editor, range));
            actions.add(new MyRollbackLineStatusRangeAction(editor, range));
            actions.add(new ShowLineStatusRangeDiffAction(editor, range));
            actions.add(new CopyLineStatusRangeAction(editor, range));
            actions.add(new ToggleByWordDiffAction(editor, range, mousePosition));
            return actions;
        }

        public class MyRollbackLineStatusRangeAction extends RangeMarkerAction {
            MyRollbackLineStatusRangeAction(@NotNull Editor editor, @NotNull Range range) {
                super(editor, range, IdeActions.SELECTED_CHANGES_ROLLBACK);
            }

            @Override
            protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
                return true;
            }

            @Override
            protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
                RollbackLineStatusAction.rollback(myTracker, range, editor);
            }

        }
    }

}