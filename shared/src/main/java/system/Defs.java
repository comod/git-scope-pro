package system;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;

public class Defs {
    public static String APPLICATION_NAME = "Git Scope";
    public static String TOOL_WINDOW_NAME = "Git Scope";
    public static Icon ICON = AllIcons.Actions.Diff;

    /**
     * Label of the trailing "add a scope" tab. Lives here rather than in ViewService because the
     * frontend needs it too, to recognise the special tabs in the tab context menu.
     */
    public static final String PLUS_TAB_LABEL = "+";

    /**
     * Global logger category for Git Scope plugin.
     * To enable debug logging for all Git Scope components, add this to Debug Log Settings:
     * #gitscope
     */
    public static final String LOG_CATEGORY = "gitscope";

    /**
     * Creates a logger instance for the given class that uses the global Git Scope category.
     * This allows enabling all plugin debug logs with a single setting: #gitscope
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getInstance(LOG_CATEGORY + "." + clazz.getSimpleName());
    }
}
