package state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * PersistentStateComponent keeps project config values.
 * Similar notion of 'preference' in Android
 */
@com.intellij.openapi.components.State(
    name = "GitScope",
    storages = {
        @Storage(
                value="GitScope.xml"
        )
    },
    reloadable=true
)
public class State implements PersistentStateComponent<State> {

    /**
     * HOWTO:
     * - Add to plugin.xml: <projectService serviceInterface="config.Config" serviceImplementation="config.Config"/>
     * - To Create a "node" just add class property: public String data = "";
     * -- Implement Getter and Setter
     * --- By using the setter the data is saved
     */

    //    public String history; // JSON Converted JList

    public String targetBranch = "";

    public Map<String, String> repositoryTargetBranchMap = new HashMap<>();

    public String getTargetBranch() {
        return targetBranch;
    }

    public void setTargetBranch(String targetBranch) {
        this.targetBranch = targetBranch;
    }

    public Map<String, String> getRepositoryTargetBranchMap() {
        return repositoryTargetBranchMap;
    }

    public void setRepositoryTargetBranchMap(Map<String, String> repositoryTargetBranchMap) {
        this.repositoryTargetBranchMap = repositoryTargetBranchMap;
    }

    //    public List<String> getHistory() {
//
////        final DefaultListModel defaultListModel = new DefaultListModel();
//
//        System.out.println("Get historyFromJson");
//        System.out.println(this.history);
//        Gson gson = new GsonBuilder().create();
//        return gson.fromJson(this.history, new TypeToken<List<String>>() {}.getType());
//
//    }
//
//    public void setHistory(JList history) {
//
//        System.out.println("Set history");
//
//        Gson gson = new GsonBuilder().create();
//
//        // JLIST 2 List<String>
//        Type listType = new TypeToken<List<String>>() {}.getType();
//        List<String> listForJsonExport = new LinkedList<String>();
//
//        ListModel model = history.getModel();
//
//        for(int i=0; i < model.getSize(); i++){
//            Object o =  model.getElementAt(i);
//            listForJsonExport.add(o.toString());
//        }
//
//        String json = gson.toJson(listForJsonExport);
//        this.history = json;
//
//    }

    @Nullable
    @Override
    public State getState() {
        return this;
    }

    @Override
    public void loadState(State state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    @Nullable
    public static State getInstance(Project project) {
        State sfec = project.getService(State.class);
        return sfec;
    }
}
