package implementation.targetBranchWidget;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import implementation.Manager;
import listener.ChangeActionNotifier;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.awt.event.MouseEvent;

public abstract class MyDvcsStatusWidget<T extends Repository> extends EditorBasedWidget
  implements
        StatusBarWidget.MultipleTextValuesPresentation,
        StatusBarWidget.Multiframe
{

  @NotNull private final String myPrefix;
  private final Manager manager;

  @Nullable private String myText;
  @Nullable private String myTooltip;

  protected MyDvcsStatusWidget(@NotNull Project project, @NotNull String prefix) {
    super(project);
    myPrefix = prefix;

    manager = project.getService(Manager.class);
  }

  @Nullable
  protected abstract T guessCurrentRepository(@NotNull Project project);

  @NotNull
  protected abstract ListPopup getPopup(@NotNull Project project, @NotNull T repository);

  protected abstract void subscribeToRepoChangeEvents(@NotNull Project project);

  protected abstract void rememberRecentRoot(@NotNull String path);

  public void activate() {
    Project project = getProject();
    if (project != null) {
      installWidgetToStatusBar(project, this);
    }
  }

  @Nullable
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
//    LOG.debug("selection changed");
    update();
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
//    LOG.debug("file opened");
    update();
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
//    LOG.debug("file closed");
    update();
  }

  @CalledInAwt
  @Nullable
  @Override
  public String getSelectedValue() {
    return "➙ " + Defs.APPLICATION_NAME + ": " + myText;
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Nullable
  @Override
  public ListPopup getPopupStep() {
    Project project = getProject();
    if (project == null || project.isDisposed()) return null;
    T repository = guessCurrentRepository(project);
    if (repository == null) return null;

    return getPopup(project, repository);
  }

  @Nullable
  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    // has no effect since the click opens a list popup, and the consumer is not called for the MultipleTextValuesPresentation
    return null;
  }

  public void updateLater() {
    Project project = getProject();
    if (project != null && !project.isDisposed()) {
      ApplicationManager.getApplication().invokeLater(() -> {
//        LOG.debug("update after repository change");
        update();
      }, project.getDisposed());
    }
  }

  @CalledInAwt
  private void update() {
    myText = null;
    myTooltip = null;

    Project project = getProject();
    if (project == null || project.isDisposed()) return;
    T repository = guessCurrentRepository(project);
    if (repository == null) return;

//    int maxLength = MAX_STRING.length() - 1; // -1, because there are arrows indicating that it is a popup
    myText = manager.getTargetBranchDisplay();
//    myTooltip = getToolTip(project);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
    rememberRecentRoot(repository.getRoot().getPath());
  }

  private void installWidgetToStatusBar(@NotNull final Project project, @NotNull final StatusBarWidget widget) {
    ApplicationManager.getApplication().invokeLater(() -> {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null && !isDisposed()) {
        subscribeToMappingChanged();
        subscribeToRepoChangeEvents(project);
        subscribeToChangeActionNotifier();
        update();
      }
    }, project.getDisposed());
  }

  private void subscribeToMappingChanged() {
    myProject.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, new VcsRepositoryMappingListener() {
      @Override
      public void mappingChanged() {
//        LOG.debug("repository mappings changed");
        updateLater();
      }
    });
  }

  private void subscribeToChangeActionNotifier() {
    myProject.getMessageBus().connect().subscribe(ChangeActionNotifier.CHANGE_ACTION_TOPIC, new ChangeActionNotifier() {
      @Override
      public void doAction(String context) {
        updateLater();
      }
    });
  }
}
