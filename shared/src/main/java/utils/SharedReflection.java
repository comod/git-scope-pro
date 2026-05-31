package utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Reflection bridge for IntelliJ Platform APIs that {@code verifyPlugin} flags as
 * internal.
 *
 * <h3>APIs bridged reflectively</h3>
 *
 * <h4>{@link #getGutterArea} — gutter marker alignment</h4>
 * <ul>
 *   <li>{@code com.intellij.openapi.diff.LineStatusMarkerDrawUtil} (class)</li>
 *   <li>{@code LineStatusMarkerDrawUtil.getGutterArea(Editor)} (static method)</li>
 *   <li>{@code LineStatusMarkerDrawUtil$IntPair.first} / {@code .second}
 *       (public int fields on the return type)</li>
 * </ul>
 */
public final class SharedReflection {

    private static final Logger LOG = Defs.getLogger(SharedReflection.class);

    private static final @Nullable MethodHandle GUTTER_GET_AREA;
    private static final @Nullable MethodHandle INT_PAIR_FIRST;
    private static final @Nullable MethodHandle INT_PAIR_SECOND;

    static {
        MethodHandle gutterArea = null;
        MethodHandle pairFirst = null;
        MethodHandle pairSecond = null;
        try {
            Class<?> drawUtilClass = Class.forName("com.intellij.openapi.diff.LineStatusMarkerDrawUtil");
            Method m = drawUtilClass.getMethod("getGutterArea", Editor.class);
            gutterArea = MethodHandles.publicLookup().unreflect(m)
                    .asType(MethodType.genericMethodType(1));
            Class<?> intPairClass = m.getReturnType();
            pairFirst = MethodHandles.publicLookup().unreflectGetter(intPairClass.getField("first"))
                    .asType(MethodType.methodType(int.class, Object.class));
            pairSecond = MethodHandles.publicLookup().unreflectGetter(intPairClass.getField("second"))
                    .asType(MethodType.methodType(int.class, Object.class));
        } catch (Exception e) {
            LOG.debug("SharedReflection: LineStatusMarkerDrawUtil.getGutterArea not available — " + e.getMessage());
        }
        GUTTER_GET_AREA = gutterArea;
        INT_PAIR_FIRST = pairFirst;
        INT_PAIR_SECOND = pairSecond;
    }

    private SharedReflection() {}

    public static int @Nullable [] getGutterArea(@NotNull Editor editor) {
        if (GUTTER_GET_AREA == null || INT_PAIR_FIRST == null || INT_PAIR_SECOND == null) {
            return null;
        }
        try {
            Object intPair = GUTTER_GET_AREA.invoke(editor);
            int x = (int) INT_PAIR_FIRST.invoke(intPair);
            int endX = (int) INT_PAIR_SECOND.invoke(intPair);
            return new int[]{x, endX};
        } catch (Throwable t) {
            LOG.warn("SharedReflection: getGutterArea invocation failed", t);
            return null;
        }
    }
}
