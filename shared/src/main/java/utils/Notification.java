package utils;

import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import system.Defs;

import javax.swing.*;

public class Notification {

    public static void notify(String title, String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Notifications.Bus.notify(new com.intellij.notification.Notification(Defs.APPLICATION_NAME, title + ".", message + ".", NotificationType.INFORMATION));
            }
        });
    }

}
