package utils;

import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

/**
 * Utility to call commit.getChanges() via reflection with a strict expectation:
 * - Method name: getChanges
 * - Signature: no-arg
 * - Return type: Collection<Change>
 *
 * If anything deviates, logs an error and returns an empty list.
 */
public final class GitCommitReflection {

    private static final com.intellij.openapi.diagnostic.Logger LOG = Defs.getLogger(GitCommitReflection.class);

    private GitCommitReflection() {}

    /**
     * Invoke commit.getChanges() via reflection and return as Collection<Change>.
     * Logs error and returns empty list on any mismatch or failure.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public static Collection<Change> getChanges(@Nullable Object commit) {
        if (commit == null) {
            LOG.error("GitCommitReflectionUtil.getChanges: commit is null");
            return Collections.emptyList();
        }

        try {
            Method m = commit.getClass().getMethod("getChanges");
            Object result = m.invoke(commit);

            if (result == null) {
                LOG.error("GitCommitReflectionUtil.getChanges: returned null");
                return Collections.emptyList();
            }

            if (!(result instanceof Collection<?>)) {
                LOG.error("GitCommitReflectionUtil.getChanges: unexpected return type: " + result.getClass().getName());
                return Collections.emptyList();
            }

            // We assume current API returns Collection<Change>; if not, this will throw ClassCastException
            return (Collection<Change>) result;

        } catch (NoSuchMethodException e) {
            LOG.error("GitCommitReflectionUtil.getChanges: method getChanges() not found on " + commit.getClass().getName(), e);
        } catch (ClassCastException e) {
            LOG.error("GitCommitReflectionUtil.getChanges: return type is not Collection<Change>", e);
        } catch (Throwable t) {
            LOG.error("GitCommitReflectionUtil.getChanges: invocation failed", t);
        }
        return Collections.emptyList();
    }
}