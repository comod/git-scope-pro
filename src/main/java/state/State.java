package state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import model.MyModel;
import model.MyModelBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
@com.intellij.openapi.components.State(
        name = "GitScope",
        storages = {@Storage(value = "GitScopePro.xml")},
        reloadable = true
)
public class State implements PersistentStateComponent<State> {

    @OptionTag(converter = MyModelConverter.class)
    public List<MyModelBase> modelData;
    public Boolean twoDotsCheckbox = false;

    public List<MyModelBase> getModelData() {
        return modelData;
    }

    public void setModelData(List<MyModelBase> modelData) {
        this.modelData = modelData;
    }

    public Boolean getTwoDotsCheckbox() {
        return twoDotsCheckbox;
    }

    public void setTwoDotsCheckbox(Boolean value) {
        twoDotsCheckbox = value;
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