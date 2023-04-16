package state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
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
                        value = "GitScope.xml"
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

    public Map<String, String> repositoryTargetBranchMap = new HashMap<>();

    public Map<String, String> toolWindowTabMap = new HashMap<>();

    @Nullable
    public static State getInstance(Project project) {
        State sfec = project.getService(State.class);
        return sfec;
    }

    public Map<String, String> getToolWindowTabMap() {
        return toolWindowTabMap;
    }

    public void setToolWindowTabMap(Map<String, String> toolWindowTabMap) {
        this.toolWindowTabMap = toolWindowTabMap;
    }

    public ToolWindowTabVo getVo(String id) {
        VoConverter voCon = new VoConverter();
        if (!this.toolWindowTabMap.containsKey(id)) {
            ToolWindowTabVo defaultVo = new ToolWindowTabVo("default");
            setVo(id, defaultVo);
        }
        String value = this.toolWindowTabMap.get(id);
        return voCon.fromString(value);
    }

    public void setVo(String id, ToolWindowTabVo vo) {
        VoConverter voCon = new VoConverter();
        this.toolWindowTabMap.put(id, voCon.toString(vo));
    }

    public Map<String, String> getRepositoryTargetBranchMap() {
        return repositoryTargetBranchMap;
    }

    public void setRepositoryTargetBranchMap(Map<String, String> repositoryTargetBranchMap) {
        this.repositoryTargetBranchMap = repositoryTargetBranchMap;
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
