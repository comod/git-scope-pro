package implementation.scope;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import system.Defs;

import java.util.*;

public class MyScope {
    public static final String OLD_SCOPE_ID = "GitScopePro";
    public static final String SCOPE_ID = "GitScope";
    private final NamedScopeManager scopeManager;
    private final Project myProject;
    private MyPackageSet myPackageSet;

    public MyScope(Project project) {
        this.scopeManager = NamedScopeManager.getInstance(project);
        this.myProject = project;
        this.createSearchScope();
        this.updateProjectScope();
    }

    public void createSearchScope() {
        this.myPackageSet = new MyPackageSet();
    }

    private void updateProjectScope()
    {
        NamedScope myScope = new NamedScope(
                SCOPE_ID,
                () -> "Git Scope",
                Defs.ICON,
                this.myPackageSet
        );

        List<NamedScope> scopes = new ArrayList<>(Arrays.asList(scopeManager.getEditableScopes()));
        scopes.removeIf(scope -> SCOPE_ID.equals(scope.getScopeId()));
        scopes.removeIf(scope -> OLD_SCOPE_ID.equals(scope.getScopeId()));
        scopes.add(myScope);
        scopeManager.setScopes(scopes.toArray(new NamedScope[0]));
    }

    private void updateProjectFilter()
    {
        InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
        profile.scopesChanged();
        scopeManager.setScopes(scopeManager.getEditableScopes());
        ProjectView.getInstance(myProject).refresh();
    }

    public void update(Collection<Change> changes) {
        // Only create/update scope if we have actual changes
        if (changes != null && !changes.isEmpty()) {
            if (this.myPackageSet == null) {
                createSearchScope();
            }
            this.myPackageSet.setChanges(changes);
        } else {
            // If no changes, set empty collection instead of null
            if (this.myPackageSet != null) {
                this.myPackageSet.setChanges(new ArrayList<>());
            }
        }

        if (changes != null) {
            ApplicationManager.getApplication().invokeLater(this::updateProjectFilter);
        }
    }
}
