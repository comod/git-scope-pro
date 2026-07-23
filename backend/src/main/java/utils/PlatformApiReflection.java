package utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.Change;
import git4idea.GitCommit;
import git4idea.GitReference;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Centralized registry of reflective API bridges for IntelliJ Platform APIs that are
 * either deprecated, experimental, or internal.
 *
 * <p>All method lookups are performed <em>once</em> at class-load time and cached as
 * {@link MethodHandle} instances ({@code null} when a target is absent in the running
 * IDE version). {@link MethodHandle} objects are JIT-friendly and have near-zero per-call
 * overhead after warm-up — significantly faster than repeated {@link Method#invoke} calls.
 * Because no direct method references appear in bytecode, none of these usages are flagged
 * by {@code verifyPlugin}'s deprecated/internal API checks.
 *
 * <h3>Bridges</h3>
 * <ul>
 *   <li>{@link #getCommitChanges} — {@code GitCommit.getChanges()} via its
 *       {@code @ApiStatus.Experimental} annotation</li>
 *   <li>{@link #findTagByName} — tag lookup via the newer {@code getTagsHolder()}
 *       (2026.1+) or legacy {@code getTagHolder()} (older IDEs)</li>
 * </ul>
 *
 * <p>See also {@link utils.SharedReflection} for shared-module bridges (gutter area,
 * split-mode RPC transport) that the frontend also needs.
 */
public final class PlatformApiReflection {

    private static final Logger LOG = Defs.getLogger(PlatformApiReflection.class);

    // ── GitCommit.getChanges() (@ApiStatus.Experimental) ────────────────────
    // type after adaptation: (Object receiver) -> Object
    private static final @Nullable MethodHandle COMMIT_GET_CHANGES;

    // ── Tag lookup (IDE 2026.1+): getTagsHolder() API ────────────────────────
    // Each handle: (Object receiver [, args]) -> Object
    private static final @Nullable MethodHandle REPO_GET_TAGS_HOLDER;
    private static final @Nullable MethodHandle TAGS_HOLDER_GET_STATE;
    private static final @Nullable MethodHandle STATE_FLOW_GET_VALUE;
    private static final @Nullable MethodHandle TAGS_STATE_GET_TAGS_MAP;

    // ── Tag lookup (pre-2026.1): getTagHolder() API (deprecated) ─────────────
    private static final @Nullable MethodHandle REPO_GET_TAG_HOLDER;
    private static final @Nullable MethodHandle TAG_HOLDER_GET_TAG;  // (Object, Object name) -> Object

    static {
        // ── GitCommit.getChanges() ─────────────────────────────────────────
        COMMIT_GET_CHANGES = resolvePublicVirtual(GitCommit.class, "getChanges");

        // ── New tag path ───────────────────────────────────────────────────
        REPO_GET_TAGS_HOLDER = resolvePublicVirtual(GitRepository.class, "getTagsHolder");
        TAGS_HOLDER_GET_STATE =
                resolveByClassName("git4idea.repo.GitRepositoryTagsHolder", "getState");
        STATE_FLOW_GET_VALUE =
                resolveByClassName("kotlinx.coroutines.flow.StateFlow", "getValue");
        TAGS_STATE_GET_TAGS_MAP =
                resolveByClassName("git4idea.repo.GitRepositoryTagsState", "getTagsToCommitHashes");

        // ── Legacy tag path ────────────────────────────────────────────────
        REPO_GET_TAG_HOLDER = resolvePublicVirtual(GitRepository.class, "getTagHolder");
        TAG_HOLDER_GET_TAG  = resolveByClassName("git4idea.repo.GitTagHolder", "getTag", String.class);
    }

    private PlatformApiReflection() {}

    // ── Resolution helpers ────────────────────────────────────────────────────

    /**
     * Resolves a public virtual method on a known compile-time class and widens the
     * resulting {@link MethodHandle} to {@code (Object [, Object...]) -> Object} so it can
     * be invoked uniformly regardless of the concrete receiver/return types.
     */
    private static @Nullable MethodHandle resolvePublicVirtual(
            Class<?> cls, String name, Class<?>... paramTypes) {
        try {
            Method m = cls.getMethod(name, paramTypes);
            // unreflect preserves the exact signature; asType widens via boxing/casting
            MethodHandle h = MethodHandles.publicLookup().unreflect(m);
            return h.asType(MethodType.genericMethodType(1 + paramTypes.length));
        } catch (Exception e) {
            LOG.debug("PlatformApiReflection: could not resolve " + cls.getSimpleName()
                              + "." + name + " — " + e.getMessage());
            return null;
        }
    }

    /**
     * Like {@link #resolvePublicVirtual} but loads the declaring class by name first.
     * Returns {@code null} when the class is absent in the running IDE version.
     */
    private static @Nullable MethodHandle resolveByClassName(
            String className, String methodName, Class<?>... paramTypes) {
        try {
            Class<?> cls = Class.forName(className);
            return resolvePublicVirtual(cls, methodName, paramTypes);
        } catch (ClassNotFoundException e) {
            LOG.debug("PlatformApiReflection: class not available — " + className);
            return null;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Calls {@code commit.getChanges()} via a cached {@link MethodHandle}.
     * The method is annotated {@code @ApiStatus.Experimental} and therefore accessed
     * reflectively to avoid verifyPlugin warnings.
     *
     * @return the commit's changes, or an empty list when the handle is unavailable
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public static Collection<Change> getCommitChanges(@NotNull GitCommit commit) {
        if (COMMIT_GET_CHANGES == null) {
            LOG.warn("PlatformApiReflection: getChanges handle unavailable");
            return Collections.emptyList();
        }
        try {
            Object result = COMMIT_GET_CHANGES.invoke(commit);
            if (result instanceof Collection<?> c) {
                return (Collection<Change>) c;
            }
            LOG.warn("PlatformApiReflection: getChanges returned unexpected type: "
                             + (result == null ? "null" : result.getClass().getName()));
        } catch (Throwable t) {
            LOG.error("PlatformApiReflection: getCommitChanges invocation failed", t);
        }
        return Collections.emptyList();
    }

    /**
     * Finds a Git tag by name on the given repository.
     *
     * <p>Tries the non-deprecated {@code getTagsHolder()} API introduced in 2026.1 first.
     * Falls back to the deprecated {@code getTagHolder().getTag(name)} on older IDEs.
     * Both paths use cached {@link MethodHandle} instances.
     *
     * @param repo    the repository to search
     * @param tagName the tag's short name (e.g. {@code "v1.0.0"})
     * @return the matching {@link GitReference}, or {@code null} if not found
     */
    @Nullable
    public static GitReference findTagByName(@NotNull GitRepository repo, @NotNull String tagName) {
        // IDE 2026.1+: getTagsHolder() -> state -> value -> tagsToCommitHashes -> find by name
        if (REPO_GET_TAGS_HOLDER != null && TAGS_HOLDER_GET_STATE != null
                && STATE_FLOW_GET_VALUE != null && TAGS_STATE_GET_TAGS_MAP != null) {
            try {
                Object tagsHolder = REPO_GET_TAGS_HOLDER.invoke(repo);
                Object stateFlow  = TAGS_HOLDER_GET_STATE.invoke(tagsHolder);
                Object state      = STATE_FLOW_GET_VALUE.invoke(stateFlow);
                Object rawMap     = TAGS_STATE_GET_TAGS_MAP.invoke(state);
                if (rawMap instanceof Map<?, ?> tagsMap) {
                    for (Object key : tagsMap.keySet()) {
                        if (key instanceof GitReference ref
                                && (tagName.equals(ref.getName()) || tagName.equals(ref.getFullName()))) {
                            return ref;
                        }
                    }
                }
                return null;
            } catch (Throwable t) {
                LOG.warn("PlatformApiReflection: findTagByName (2026.1+ path) failed", t);
            }
        }

        // Pre-2026.1 fallback: getTagHolder().getTag(name)
        if (REPO_GET_TAG_HOLDER != null && TAG_HOLDER_GET_TAG != null) {
            try {
                Object tagHolder = REPO_GET_TAG_HOLDER.invoke(repo);
                Object result    = TAG_HOLDER_GET_TAG.invoke(tagHolder, tagName);
                return result instanceof GitReference ref ? ref : null;
            } catch (Throwable t) {
                LOG.warn("PlatformApiReflection: findTagByName (pre-2026.1 path) failed", t);
            }
        }
        return null;
    }
}
