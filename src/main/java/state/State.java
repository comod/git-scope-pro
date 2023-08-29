package state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
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
 */

/**
 * PersistentStateComponent keeps project config values.
 * Similar notion of 'preference' in Android
 */
@com.intellij.openapi.components.State(
        name = "GitScope",
        storages = {@Storage(value = "GitScopePro.xml")},
        reloadable = true
)
public class State implements PersistentStateComponent<State> {

    public String data;

    public Boolean ThreeDotsCheckBox = true;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void save(List<MyModel> modelCollection) {
        List<MyModelBase> persistList = new ArrayList<>();
        modelCollection.forEach(model -> {
            MyModelBase myModelBase = new MyModelBase();
            myModelBase.setTargetBranchMap(model.getTargetBranchMap());
            persistList.add(myModelBase);
        });
        MyModelConverter converter = new MyModelConverter();
        setData(converter.toString(persistList));
    }

    public List<MyModelBase> load() {
        MyModelConverter converter = new MyModelConverter();
        return converter.fromString(getData());
    }

    @Nullable
    @Override
    public State getState() {
        return this;
    }

    public Boolean getThreeDotsCheckBox() {
        return ThreeDotsCheckBox;
    }

    public void setThreeDotsCheckBox(Boolean threeDotsCheckBox) {
        ThreeDotsCheckBox = threeDotsCheckBox;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
