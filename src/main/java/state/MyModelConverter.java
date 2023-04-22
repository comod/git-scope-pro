package state;


import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.intellij.util.xmlb.Converter;
import model.MyModel;
import model.MyModelBase;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

class MyModelConverter extends Converter<List<MyModelBase>> {
    public List<MyModelBase> fromString(@NotNull String value) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<MyModelBase>>() {
        }.getType();
        return gson.fromJson(value, listType);
    }

    public String toString(@NotNull List<MyModelBase> object) {
        Gson gson = new Gson();
        return gson.toJson(object);
    }
}
