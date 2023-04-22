package implementation.scope;

import system.Defs;

import java.util.function.Supplier;

public class MyScopeNameSupplier implements Supplier<String> {
    @Override
    public String get() {
        return Defs.APPLICATION_NAME;
    }
}
