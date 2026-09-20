package state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import model.MyModelBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * HOWTO:
 * - Add to plugin.xml: <projectService serviceInterface="config.Config" serviceImplementation="config.Config"/>
 * - To Create a "node" just add class property: public String data = "";
 * -- Implement Getter and Setter
 * --- By using the setter the data is saved
 * --- Map<String, String> possible
 * --- Map<String, Object> not possible
 * <p>
 * PersistentStateComponent keeps project config values.
 * Similar notion of 'preference' in Android
 */
/* GitScopePro.xml is a project-shared file (not gitignored by default), so it stored repo
 * target-branch selections keyed by an absolute path that could point at the wrong machine's
 * checkout for every collaborator but the one who saved it (#110). workspace.xml is per-user
 * and local, which is what this data actually is. The deprecated storage is only read when
 * workspace.xml has nothing yet (existing users keep their saved tabs on upgrade), and is never
 * written to again once it's read once -- see Storage.deprecated() javadoc. */
@com.intellij.openapi.components.State(
        name = "GitScope",
        storages = {
                @Storage(StoragePathMacros.WORKSPACE_FILE),
                @Storage(value = "GitScopePro.xml", deprecated = true)
        }
)
public class State implements PersistentStateComponent<State> {

    @OptionTag(converter = MyModelConverter.class)
    public List<MyModelBase> modelData;
    public Boolean threeDotsCheckBox = false;

    /**
     * Per-project "show untracked/deleted files" toggle (Git Scope window buttons, #111). Null
     * until {@link service.ViewService} seeds it from {@link settings.GitScopeSettings}'s
     * new-project default on first load; from then on this project's own value is authoritative.
     */
    public Boolean showUntrackedFiles;
    public Boolean showDeletedFiles;

    public List<MyModelBase> getModelData() {
        return modelData;
    }

    public void setModelData(List<MyModelBase> modelData) {
        this.modelData = modelData;
    }

    public Boolean getThreeDotsCheckBox() {
        return threeDotsCheckBox;
    }

    public void setThreeDotsCheckBox(Boolean value) {
        threeDotsCheckBox = value;
    }

    public Boolean getShowUntrackedFiles() {
        return showUntrackedFiles;
    }

    public void setShowUntrackedFiles(Boolean value) {
        showUntrackedFiles = value;
    }

    public Boolean getShowDeletedFiles() {
        return showDeletedFiles;
    }

    public void setShowDeletedFiles(Boolean value) {
        showDeletedFiles = value;
    }

    // In your State class, add these methods:
    private Integer currentTabIndex;

    public Integer getCurrentTabIndex() {
        return currentTabIndex;
    }

    public void setCurrentTabIndex(Integer currentTabIndex) {
        this.currentTabIndex = currentTabIndex;
    }

    @Nullable
    @Override
    public State getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}