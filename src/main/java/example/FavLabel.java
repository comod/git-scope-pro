package example;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FavLabel {
    private final String name;
    private final boolean isFav;

    public FavLabel(String name, boolean isFav) {
        this.name = name;
        this.isFav = isFav;
    }

    public static FavLabel create(String name, boolean isFav) {
        return new FavLabel(name, isFav);
    }

    public static FavLabel create(String name) {
        return new FavLabel(name, false);
    }

    public boolean isFav() {
        return isFav;
    }

    public @NotNull String getName() {
        return name;
    }


    @Override
    public String toString() {
        return getName();
    }
}
