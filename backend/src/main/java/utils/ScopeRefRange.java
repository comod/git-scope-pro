package utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parses the Git range syntax a scope can carry: the {@code <ref>..HEAD} produced by the
 * "Only Changes Since Common Ancestor" checkbox, and anything a user types manually.
 *
 * <p>A scope has exactly two meanings, and a range expresses the second one:
 * <ul>
 *   <li>a bare ref ({@code main}) — compare that ref directly to HEAD, i.e. {@code git diff main HEAD}</li>
 *   <li>a range against HEAD ({@code main..HEAD}, {@code main...HEAD}) — everything on HEAD since it
 *       diverged from the ref, i.e. {@code git diff main...HEAD}, the diff a pull request shows</li>
 * </ul>
 *
 * <p>Both dot forms are accepted for the second meaning: a strict two-dot reading would be identical
 * to selecting the bare ref, which the UI already offers, and older saved scopes use two dots. The
 * right-hand side must be HEAD (or empty, which Git itself defaults to HEAD) — any other target is a
 * comparison this plugin cannot express, and is reported rather than silently misread as
 * "since the common ancestor with HEAD".
 *
 * <p>Git ref names may not contain two consecutive dots (see {@code git check-ref-format}), so the
 * first {@code ".."} is an unambiguous separator.
 *
 * <p>Deliberately free of IntelliJ Platform types so it can be unit tested without an IDE.
 */
public final class ScopeRefRange {

    private static final String SEPARATOR = "..";
    private static final String HEAD = "HEAD";

    private ScopeRefRange() {}

    /**
     * Returns whether the scope uses Git range syntax at all, supported or not.
     */
    public static boolean isRange(@Nullable String scopeRef) {
        return scopeRef != null && scopeRef.contains(SEPARATOR);
    }

    /**
     * Returns the ref on the left of a supported range, whose merge base with HEAD is the diff base.
     *
     * @return the selected ref, or {@code null} when the scope is not a range, has no left side, or
     * targets something other than HEAD
     */
    @Nullable
    public static String selectedRef(@Nullable String scopeRef) {
        if (scopeRef == null) {
            return null;
        }

        int separator = scopeRef.indexOf(SEPARATOR);
        if (separator < 0) {
            return null;
        }

        String selectedRef = scopeRef.substring(0, separator).trim();
        if (selectedRef.isEmpty()) {
            return null;
        }

        String target = scopeRef.substring(separator + SEPARATOR.length());
        if (target.startsWith(".")) {
            // Three-dot form: drop the extra dot to expose the target ref.
            target = target.substring(1);
        }
        target = target.trim();

        // An omitted target means HEAD in Git, so "main.." is the same request as "main..HEAD".
        return target.isEmpty() || HEAD.equals(target) ? selectedRef : null;
    }

    /**
     * Strips a supported {@code ..HEAD}/{@code ...HEAD} suffix so the scope can be shown or used as a
     * plain ref. Refs without a range, and ranges this plugin does not support, are returned unchanged.
     */
    @NotNull
    public static String stripRange(@NotNull String scopeRef) {
        String selectedRef = selectedRef(scopeRef);
        return selectedRef == null ? scopeRef : selectedRef;
    }
}
