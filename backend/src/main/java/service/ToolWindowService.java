package service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import listener.MyTabContentListener;
import model.MyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;
import toolwindow.TabOperations;
import toolwindow.ToolWindowView;
import toolwindow.elements.VcsTree;

import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class ToolWindowService implements ToolWindowServiceInterface, Disposable {
    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(ToolWindowService.class);

    private final Project project;
    private final Map<Content, ToolWindowView> contentToViewMap = new HashMap<>();
    private final TabOperations tabOperations;

    public ToolWindowService(Project project) {
        this.project = project;
        this.tabOperations = new TabOperations(project);
    }

    @Override
    public void dispose() {
        // Dispose all ToolWindowView instances to clean up UI components
        for (ToolWindowView view : contentToViewMap.values()) {
            if (view != null) {
                view.dispose();
            }
        }

        // Clear the content to view map to release memory
        contentToViewMap.clear();
    }

    @Override
    public void removeAllTabs() {
        getContentManager().removeAllContents(true);
        contentToViewMap.clear();
    }

    public ToolWindow getToolWindow() {
        if (project.isDisposed()) {
            return null;
        }
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        return toolWindowManager.getToolWindow(Defs.TOOL_WINDOW_NAME);
    }

    @NotNull
    private ContentManager getContentManager() {
        ToolWindow toolWindow = getToolWindow();
        if (toolWindow == null) {
            throw new IllegalStateException("Tool window is not available");
        }
        return toolWindow.getContentManager();
    }

    public void addTab(MyModel myModel, String tabName, boolean closeable) {
        ToolWindowView toolWindowView = new ToolWindowView(project, myModel);
        Content content = ContentFactory.getInstance().createContent(toolWindowView.getRootPanel(), tabName, false);
        content.setCloseable(closeable);
        contentToViewMap.put(content, toolWindowView);
        ContentManager contentManager = getContentManager();
        contentManager.addContent(content);
        content.setDisplayName(tabName);
    }

    @Override
    public VcsTree getVcsTree() {
        try {
            // Get the currently selected content
            Content selectedContent = getContentManager().getSelectedContent();
            if (selectedContent == null) {
                return null;
            }

            // Get the ToolWindowView for this content
            ToolWindowView toolWindowView = contentToViewMap.get(selectedContent);
            if (toolWindowView != null) {
                return toolWindowView.getVcsTree();
            }

            return null;

        } catch (Exception e) {
            // Log the error if needed
            return null;
        }
    }

    public void addListener() {
        ContentManager contentManager = getContentManager();
        contentManager.addContentManagerListener(new MyTabContentListener(project));
    }

    @Override
    public void setupTabTooltip(MyModel model) {
        tabOperations.setupTabTooltip(model, contentToViewMap);
    }

    @Override
    public void changeTabName(String title) {
        tabOperations.changeTabName(title, getContentManager());
    }

    @Override
    public void changeTabNameForModel(MyModel model, String title) {
        // Find the Content for this model
        Content targetContent = null;
        for (Map.Entry<Content, ToolWindowView> entry : contentToViewMap.entrySet()) {
            ToolWindowView view = entry.getValue();
            if (view != null && view.getModel() == model) {
                targetContent = entry.getKey();
                break;
            }
        }

        // Update the tab name for the specific content
        if (targetContent != null) {
            String currentName = targetContent.getDisplayName();
            if (!currentName.equals(title)) {
                targetContent.setDisplayName(title);
            }
        }
    }

    public void removeTab(int index) {
        @Nullable Content content = getContentManager().getContent(index);
        if (content == null) {
            return;
        }

        // Dispose the view associated with this content before removing
        ToolWindowView view = contentToViewMap.remove(content);
        if (view != null) {
            view.dispose();
        }

        getContentManager().removeContent(content, false);
    }

    public void removeCurrentTab() {
        @Nullable Content content = getContentManager().getSelectedContent();
        if (content == null) {
            return;
        }

        // Dispose the view associated with this content before removing
        ToolWindowView view = contentToViewMap.remove(content);
        if (view != null) {
            view.dispose();
        }

        getContentManager().removeContent(content, true);
    }

    public void selectNewTab() {
        int count = getContentManager().getContentCount();
        int index = count - 2;
        selectTabByIndex(index);
    }

    @Override
    public void selectTabByIndex(int index) {
        @Nullable Content content = getContentManager().getContent(index);
        if (content == null) {
            return;
        }
        getContentManager().setSelectedContent(content);
        getContentManager().requestFocus(content, true);
    }

    @Override
    public void selectFile(VirtualFile file) {
        VcsTree vcsTree = getVcsTree();
        if (vcsTree != null) {
            vcsTree.selectFile(file);
        }
    }

    @Override
    public java.util.List<String> getDisplayOrderedPaths() {
        // The tree is a Swing component, so reading its model belongs on the EDT; callers (change
        // navigation) run on background threads and should not have to know that.
        java.util.List<String> paths = new java.util.ArrayList<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            if (project.isDisposed()) return;
            try {
                VcsTree vcsTree = getVcsTree();
                if (vcsTree != null) paths.addAll(vcsTree.getDisplayOrderedPaths());
            } catch (Exception e) {
                LOG.debug("Could not read the tool window's display order", e);
            }
        }, ModalityState.any());
        return paths;
    }

    @Override
    public boolean isFocused() {
        // isActive() reads window-manager state that is only consistent on the EDT; callers (change
        // navigation) run on background threads and should not have to know that.
        boolean[] focused = {false};
        ApplicationManager.getApplication().invokeAndWait(() -> {
            if (project.isDisposed()) return;
            ToolWindow toolWindow = getToolWindow();
            focused[0] = toolWindow != null && toolWindow.isVisible() && toolWindow.isActive();
        }, ModalityState.any());
        return focused[0];
    }

    @Override
    public void restoreFocus() {
        // Queued behind the file open the caller scheduled, so the check below sees the focus as it
        // ends up rather than as it was. Focus transfers the window manager owns -- Project View
        // being shown next to us on Linux, say -- are asynchronous and can land later still; there
        // is no way to wait for those, which is why callers avoid moving the focus in the first
        // place instead of relying on this.
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            ToolWindow toolWindow = getToolWindow();
            if (toolWindow == null || !toolWindow.isVisible() || toolWindow.isActive()) return;
            LOG.debug("Returning focus to the Git Scope tool window");
            // forced=false, so a real user action that moved the focus elsewhere in the meantime
            // keeps it instead of being overridden.
            toolWindow.activate(null, true, false);
        }, ModalityState.any());
    }

    @Override
    public MyModel getModelForContent(Content content) {
        ToolWindowView toolWindowView = contentToViewMap.get(content);
        if (toolWindowView != null) {
            return toolWindowView.getModel();
        }
        return null;
    }
}