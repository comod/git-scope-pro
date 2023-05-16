package event;

import model.MyModel;

import java.util.EventListener;

abstract public class ChangeActionNotifier implements ChangeActionNotifierInterface, EventListener {
    @Override
    abstract public void doAction(MyModel context);
}