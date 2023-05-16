package state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import model.MyModel;
import model.MyModelBase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * PersistentStateComponent keeps project config values.
 * Similar notion of 'preference' in Android
 */
@com.intellij.openapi.components.State(
        name = "GitScope",
        storages = {
                @Storage(
                        value = "GitScopePro.xml"
                )
        },
        reloadable = true
)
public class State implements PersistentStateComponent<State> {

    /**
     * HOWTO:
     * - Add to plugin.xml: <projectService serviceInterface="config.Config" serviceImplementation="config.Config"/>
     * - To Create a "node" just add class property: public String data = "";
     * -- Implement Getter and Setter
     * --- By using the setter the data is saved
     * --- Map<String, String> possible
     * --- Map<String, Object> not possible
     */

    public String data;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
    //    public List<MyModelBase> collection = new ArrayList<>();
//
//    public List<MyModelBase> getCollection() {
//        return collection;
//    }
//
//    public void setCollection(List<MyModelBase> collection) {
//        this.collection = collection;
//    }
//    public List<String> collection = new ArrayList<>();
//
//    public List<String> getCollection() {
//        return collection;
//    }
//
//    public void setCollection(List<String> collection) {
//        this.collection = collection;
//    }

    public void save(List<MyModel> modelCollection) {
        List<MyModelBase> persistList = new ArrayList<>();
        modelCollection.forEach(model -> {
            MyModelBase myModelBase = new MyModelBase();
            myModelBase.setTargetBranchMap(model.getTargetBranchMap());
            persistList.add(myModelBase);
        });
        MyModelConverter converter = new MyModelConverter();
        setData(converter.toString(persistList));
//        setCollection(persistList);
//        MyModelConverter converter = new MyModelConverter();
//        List<String> newCollection = new ArrayList<>();
//
//        modelCollection.forEach(model -> {
//            MyModelBase myModelBase = new MyModelBase();
//            myModelBase.setTargetBranch(model.getTargetBranch());
//
//            String modelAsString = converter.toString(myModelBase);
//            System.out.println(modelAsString);
//            newCollection.add(modelAsString);
//        });
//        setCollection(newCollection);
    }

    public List<MyModelBase> load() {
        MyModelConverter converter = new MyModelConverter();
        return converter.fromString(data);
//        if (baseModelCollection == null) {
//            return modelCollection;
//        }
//
//
//        return modelCollection;
    }

    @Nullable
    @Override
    public State getState() {
        return this;
    }

    @Override
    public void loadState(State state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
