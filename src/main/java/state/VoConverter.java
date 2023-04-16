package state;


import com.google.gson.Gson;
import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

class VoConverter extends Converter<ToolWindowTabVo> {
    public ToolWindowTabVo fromString(String value) {
        Gson gson = new Gson();
        return gson.fromJson(value, ToolWindowTabVo.class);
    }

    public String toString(@NotNull ToolWindowTabVo object) {
        Gson gson = new Gson();
        return gson.toJson(object);
    }
}
