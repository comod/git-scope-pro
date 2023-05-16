package listener;

import com.intellij.openapi.vcs.changes.Change;
import event.ChangeActionNotifierInterface;
import model.MyModel;

import java.util.Collection;

public class ChangeActionListener implements ChangeActionNotifierInterface {
    @Override
    public void doAction(MyModel model) {
        System.out.println("invoked ChangeActionListener");
        Collection<Change> changes = model.getChanges();

        System.out.println(changes);
    }
}
