package example;

import java.util.HashMap;
import java.util.Map;

public class ViewService {

    public static TabRecord defaultRef = new TabRecord(TabRecord.head);
    public Map<TabRecord, MyModel> map = new HashMap<>();

    public TabRecord currentTab;

    public MyModel getModel(TabRecord tabRecord) {
        if (!this.map.containsKey(tabRecord)) {
            currentTab = tabRecord;
            MyModel model = new MyModel(tabRecord);
            setModel(tabRecord, model);
        }
        return this.map.get(tabRecord);
    }

    public MyModel getCurrent() {
        return getModel(currentTab);
    }

    public void setModel(TabRecord ref, MyModel model) {
        this.map.put(ref, model);
    }

}
