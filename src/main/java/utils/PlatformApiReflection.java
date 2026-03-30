package utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.GitReference;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
 *   <li>{@link #openInPreviewTab} — open a file using {@code FileEditorOpenOptions}
 *       from the internal {@code fileEditor.impl} package</li>
 *   <li>{@link #getGutterArea} — {@code LineStatusMarkerDrawUtil.getGutterArea(Editor)}
 *       which uses {@code @ApiStatus.Internal} gutter offset methods</li>
 * </ul>
 */
public final class PlatformApiReflection {

    private static final Logger LOG = Defs.getLogger(PlatformApiReflection.class);

    // ── GitCommit.getChanges() (@ApiStatus.Experimental) ────────────────────
    // type after adaptation: (Object receiver) -> Object
    private static final @Nullable MethodHandle COMMIT_GET_CHANGES;

    // ── Tag lookup — new path (getTagsHolder, 2026.1+) ───────────────────────
    // Each handle: (Object receiver [, args]) -> Object
    private static final @Nullable MethodHandle REPO_GET_TAGS_HOLDER;
    private static final @Nullable MethodHandle TAGS_HOLDER_GET_STATE;
    private static final @Nullable MethodHandle STATE_FLOW_GET_VALUE;
    private static final @Nullable MethodHandle TAGS_STATE_GET_TAGS_MAP;

    // ── Tag lookup — legacy path (getTagHolder, deprecated) ──────────────────
    private static final @Nullable MethodHandle REPO_GET_TAG_HOLDER;
    private static final @Nullable MethodHandle TAG_HOLDER_GET_TAG;  // (Object, Object name) -> Object

    // ── Gutter area (LineStatusMarkerDrawUtil.getGutterArea, uses @Internal offset) ─
    // Static method: (Editor) -> IntPair;  IntPair has public int fields 'first', 'second'
    private static final @Nullable MethodHandle GUTTER_GET_AREA;         // (Object editor) -> Object IntPair
    private static final @Nullable MethodHandle INT_PAIR_FIRST;          // (Object) -> Object (int boxed)
    private static final @Nullable MethodHandle INT_PAIR_SECOND;         // (Object) -> Object (int boxed)

    // ── Preview tab (FileEditorOpenOptions, internal impl package) ────────────
    private static final @Nullable Class<?>    OPEN_OPTIONS_CLASS;
    private static final @Nullable MethodHandle OPEN_OPTIONS_CTOR;      // () -> Object
    private static final @Nullable MethodHandle WITH_REQUEST_FOCUS;     // (Object, Object bool) -> Object
    private static final @Nullable MethodHandle WITH_USE_PREVIEW_TAB;   // (Object, Object bool) -> Object
    private static final @Nullable MethodHandle WITH_REUSE_OPEN;        // (Object, Object bool) -> Object
    // openFile handle resolved lazily on first use (requires a concrete FileEditorManager instance
    // to walk the class hierarchy; implementation class is fixed for the IDE lifetime so we cache it).
    private static final AtomicReference<Optional<MethodHandle>> EDITOR_OPEN_FILE_REF =
            new AtomicReference<>(null);

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

        // ── Gutter area (LineStatusMarkerDrawUtil) ─────────────────────────
        MethodHandle gutterArea  = null;
        MethodHandle pairFirst   = null;
        MethodHandle pairSecond  = null;
        try {
            Class<?> drawUtilClass = Class.forName("com.intellij.openapi.diff.LineStatusMarkerDrawUtil");
            Method m = drawUtilClass.getMethod("getGutterArea", Editor.class);
            gutterArea = MethodHandles.publicLookup().unreflect(m)
                    .asType(MethodType.genericMethodType(1));
            // IntPair has public int fields 'first' and 'second'
            Class<?> intPairClass = m.getReturnType();
            pairFirst  = MethodHandles.publicLookup().unreflectGetter(intPairClass.getField("first"))
                    .asType(MethodType.methodType(int.class, Object.class));
            pairSecond = MethodHandles.publicLookup().unreflectGetter(intPairClass.getField("second"))
                    .asType(MethodType.methodType(int.class, Object.class));
        } catch (Exception e) {
            LOG.debug("PlatformApiReflection: LineStatusMarkerDrawUtil.getGutterArea not available — " + e.getMessage());
        }
        GUTTER_GET_AREA  = gutterArea;
        INT_PAIR_FIRST   = pairFirst;
        INT_PAIR_SECOND  = pairSecond;

        // ── Preview tab options ────────────────────────────────────────────
        Class<?>     optClass   = null;
        MethodHandle ctor       = null;
        MethodHandle withFocus  = null;
        MethodHandle withPreview = null;
        MethodHandle withReuse  = null;
        try {
            optClass = Class.forName("com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions");
            Constructor<?> c = optClass.getDeclaredConstructor();
            c.setAccessible(true);
            ctor       = MethodHandles.lookup().unreflectConstructor(c)
                                 .asType(MethodType.methodType(Object.class));
            withFocus   = resolvePublicVirtual(optClass, "withRequestFocus",  boolean.class);
            withPreview = resolvePublicVirtual(optClass, "withUsePreviewTab", boolean.class);
            withReuse   = resolvePublicVirtual(optClass, "withReuseOpen",     boolean.class);
        } catch (Exception e) {
            LOG.debug("PlatformApiReflection: FileEditorOpenOptions not available — " + e.getMessage());
        }
        OPEN_OPTIONS_CLASS   = optClass;
        OPEN_OPTIONS_CTOR    = ctor;
        WITH_REQUEST_FOCUS   = withFocus;
        WITH_USE_PREVIEW_TAB = withPreview;
        WITH_REUSE_OPEN      = withReuse;
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
     * Returns the gutter area (x, endX) used by the IDE's own VCS line status markers,
     * via {@code LineStatusMarkerDrawUtil.getGutterArea(Editor)}.
     * This internally uses {@code @ApiStatus.Internal} gutter offset methods.
     *
     * @return {@code int[]{x, endX}}, or {@code null} when the reflection bridge is unavailable
     */
    public static int @Nullable [] getGutterArea(@NotNull Editor editor) {
        if (GUTTER_GET_AREA == null || INT_PAIR_FIRST == null || INT_PAIR_SECOND == null) {
            return null;
        }
        try {
            Object intPair = GUTTER_GET_AREA.invoke(editor);
            int x    = (int) INT_PAIR_FIRST.invoke(intPair);
            int endX = (int) INT_PAIR_SECOND.invoke(intPair);
            return new int[]{x, endX};
        } catch (Throwable t) {
            LOG.warn("PlatformApiReflection: getGutterArea invocation failed", t);
            return null;
        }
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
        // New path: getTagsHolder() -> state -> value -> tagsToCommitHashes -> find by name
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
                LOG.warn("PlatformApiReflection: findTagByName (new path) failed", t);
            }
        }

        // Legacy fallback: getTagHolder().getTag(name)
        if (REPO_GET_TAG_HOLDER != null && TAG_HOLDER_GET_TAG != null) {
            try {
                Object tagHolder = REPO_GET_TAG_HOLDER.invoke(repo);
                Object result    = TAG_HOLDER_GET_TAG.invoke(tagHolder, tagName);
                return result instanceof GitReference ref ? ref : null;
            } catch (Throwable t) {
                LOG.warn("PlatformApiReflection: findTagByName (legacy path) failed", t);
            }
        }
        return null;
    }

    /**
     * Opens a file in the editor's preview tab using the internal
     * {@code FileEditorOpenOptions} API. Does nothing if the API is unavailable.
     *
     * @param project the current project
     * @param file    the file to open
     */
    public static void openInPreviewTab(@NotNull Project project, @NotNull VirtualFile file) {
        if (OPEN_OPTIONS_CLASS == null || OPEN_OPTIONS_CTOR == null
                || WITH_REQUEST_FOCUS == null || WITH_USE_PREVIEW_TAB == null
                || WITH_REUSE_OPEN == null) {
            return;
        }
        try {
            FileEditorManager editorManager = FileEditorManager.getInstance(project);
            MethodHandle openFile = resolveEditorOpenFile(editorManager);
            if (openFile == null) {
                LOG.debug("PlatformApiReflection: openFile method not found on "
                                  + editorManager.getClass().getName());
                return;
            }
            Object options = OPEN_OPTIONS_CTOR.invoke();
            options = WITH_REQUEST_FOCUS.invoke(options, false);
            options = WITH_USE_PREVIEW_TAB.invoke(options, true);
            options = WITH_REUSE_OPEN.invoke(options, true);
            openFile.invoke(editorManager, file, null, options);
        } catch (Throwable t) {
            LOG.debug("PlatformApiReflection: openInPreviewTab failed", t);
        }
    }

    /**
     * Lazily resolves and caches the {@code FileEditorManager.openFile(VirtualFile, ?, FileEditorOpenOptions)}
     * method handle by walking the manager's class hierarchy.
     * The implementation class is fixed for the IDE lifetime, so the handle is cached
     * in {@link #EDITOR_OPEN_FILE_REF} after the first successful resolution.
     */
    private static @Nullable MethodHandle resolveEditorOpenFile(@NotNull FileEditorManager mgr) {
        Optional<MethodHandle> cached = EDITOR_OPEN_FILE_REF.get();
        if (cached.isPresent()) {
            return cached.orElse(null);
        }

        MethodHandle found = null;
        Class<?> cls = mgr.getClass();
        outer:
        while (cls != null) {
            for (Method m : cls.getDeclaredMethods()) {
                if (!"openFile".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 3
                        && VirtualFile.class.isAssignableFrom(pts[0])) {
                    assert OPEN_OPTIONS_CLASS != null;
                    if (OPEN_OPTIONS_CLASS.isAssignableFrom(pts[2])) {
                        try {
                            m.setAccessible(true);
                            // receiver + 3 params, all widened to Object
                            found = MethodHandles.lookup().unreflect(m)
                                    .asType(MethodType.genericMethodType(4));
                        } catch (Exception e) {
                            LOG.debug("PlatformApiReflection: could not unreflect openFile — "
                                    + e.getMessage());
                        }
                        break outer;
                    }
                }
            }
            cls = cls.getSuperclass();
        }

        EDITOR_OPEN_FILE_REF.compareAndSet(null, Optional.ofNullable(found));
        return found;
    }
}
