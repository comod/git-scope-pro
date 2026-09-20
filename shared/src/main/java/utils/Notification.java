package utils;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

/**
 * Balloon notifications for the "Git Scope" group, registered in gitscope.shared.xml.
 *
 * <p>Project-scoped so the balloon appears on the window the user is working in, and posted on the
 * EDT because callers reach this from RPC and background threads.
 */
public final class Notification {

    private Notification() {
    }

    public static void info(@Nullable Project project, @NotNull String title, @NotNull String message) {
        notify(project, title, message, NotificationType.INFORMATION);
    }

    public static void warn(@Nullable Project project, @NotNull String title, @NotNull String message) {
        notify(project, title, message, NotificationType.WARNING);
    }

    public static void notify(@Nullable Project project,
                              @NotNull String title,
                              @NotNull String message,
                              @NotNull NotificationType type) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project != null && project.isDisposed()) {
                return;
            }
            NotificationGroupManager.getInstance()
                    .getNotificationGroup(Defs.APPLICATION_NAME)
                    .createNotification(title, message, type)
                    .notify(project);
        });
    }
}
