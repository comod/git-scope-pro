package event;

import com.intellij.util.messages.Topic;
import model.MyModel;
import system.Defs;

public interface ChangeActionNotifierInterface {

    Topic<ChangeActionNotifierInterface> CHANGE_ACTION_TOPIC = Topic.create(Defs.APPLICATION_NAME + "SomeAction", ChangeActionNotifierInterface.class);

    void doAction(MyModel model);

}