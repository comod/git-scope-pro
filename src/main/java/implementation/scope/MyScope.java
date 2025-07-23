package implementation.scope;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.util.ArrayUtil;
import system.Defs;

import java.util.ArrayList;
import java.util.Collection;

public class MyScope {
    public static final String SCOPE_ID = "GitScopePro";
    private final NamedScopeManager scopeManager;
    private MyPackageSet myPackageSet;

    public MyScope(Project project) {
        this.scopeManager = NamedScopeManager.getInstance(project);
        this.createScope();
    }

    public void createScope() {
        this.myPackageSet = new MyPackageSet();
        NamedScope myScope = new NamedScope(SCOPE_ID, new MyScopeNameSupplier(), Defs.ICON, this.myPackageSet);
        boolean scopeExists = false;

        NamedScope[] scopes = this.scopeManager.getEditableScopes();
        NamedScope[] newNamedScopes = new NamedScope[0];

        for (NamedScope scope : scopes) {
            if (SCOPE_ID.contentEquals(scope.getScopeId())) {
                scopeExists = true;
                scope = myScope;
            }
            newNamedScopes = ArrayUtil.append(newNamedScopes, scope);
        }

        if (!scopeExists) {
            newNamedScopes = ArrayUtil.append(newNamedScopes, myScope);
        }
        this.scopeManager.setScopes(newNamedScopes);

    }

    public void update(Collection<Change> changes) {
        // Only create/update scope if we have actual changes
        if (changes != null && !changes.isEmpty()) {
            if (this.myPackageSet == null) {
                createScope();
            }
            this.myPackageSet.setChanges(changes);
        } else {
            // If no changes, set empty collection instead of null
            if (this.myPackageSet != null) {
                this.myPackageSet.setChanges(new ArrayList<>());
            }
        }
    }
}
