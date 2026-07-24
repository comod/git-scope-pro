package service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import implementation.gutter.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import system.Defs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared project service that acts as a bridge between the backend (range computation)
 * and the frontend (gutter rendering).
 * <p>
 * In local IDE mode, both sides run in the same JVM and communicate via direct method calls.
 * In remote development, this service is loaded on both sides; the platform handles
 * data synchronization.
 */
public class GutterDataService implements Disposable {

    private static final Logger LOG = Defs.getLogger(GutterDataService.class);

    /**
     * Immutable snapshot of gutter data for a single file.
     */
    public static class GutterFileData {
        public final @NotNull List<Range> ranges;
        public final @NotNull String baseContent;
        public final @Nullable String headContent;
        public final @Nullable List<Range> scopeRanges;
        /**
         * Local-change ranges (current document vs. HEAD) in current-document coordinate space —
         * i.e. the changes the IDE paints in its own gutter, which we deliberately exclude from our
         * scope painting. Published so change navigation can also stop on local changes. May be
         * null when there are no local changes or when the data was reconstructed on the frontend
         * (navigation runs on the backend, so the frontend does not need this field).
         */
        public final @Nullable List<Range> localRanges;

        public GutterFileData(@NotNull List<Range> ranges,
                              @NotNull String baseContent,
                              @Nullable String headContent,
                              @Nullable List<Range> scopeRanges) {
            this(ranges, baseContent, headContent, scopeRanges, null);
        }

        public GutterFileData(@NotNull List<Range> ranges,
                              @NotNull String baseContent,
                              @Nullable String headContent,
                              @Nullable List<Range> scopeRanges,
                              @Nullable List<Range> localRanges) {
            this.ranges = ranges;
            this.baseContent = baseContent;
            this.headContent = headContent;
            this.scopeRanges = scopeRanges;
            this.localRanges = localRanges;
        }
    }

    public interface Listener {
        void onDataUpdated(@NotNull String filePath, @NotNull GutterFileData data);
        void onDataCleared(@NotNull String filePath);
        void onAllCleared();
    }

    private final Map<String, GutterFileData> fileDataMap = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile @NotNull String scopeDisplayName = "";

    @SuppressWarnings("unused")
    public GutterDataService(Project project) {
    }

    public void publish(@NotNull String filePath, @NotNull GutterFileData data) {
        fileDataMap.put(filePath, data);
        LOG.info("GutterDataService.publish: file=" + filePath +
                ", ranges=" + data.ranges.size() +
                ", listeners=" + listeners.size() +
                ", hasBaseContent=" + (data.baseContent != null && !data.baseContent.isEmpty()) +
                ", hasHeadContent=" + (data.headContent != null));
        for (Listener l : listeners) l.onDataUpdated(filePath, data);
    }

    public void clear(@NotNull String filePath) {
        fileDataMap.remove(filePath);
        LOG.info("GutterDataService.clear: file=" + filePath);
        for (Listener l : listeners) l.onDataCleared(filePath);
    }

    public void clearAll() {
        fileDataMap.clear();
        for (Listener l : listeners) l.onAllCleared();
    }

    /**
     * Re-notifies listeners for every cached file. Used when a setting that affects the
     * rendered data (e.g. separateGutterRendering) changes, so the frontend re-renders
     * without waiting for the next scope/document update.
     */
    public void republishAll() {
        for (Map.Entry<String, GutterFileData> e : fileDataMap.entrySet()) {
            for (Listener l : listeners) l.onDataUpdated(e.getKey(), e.getValue());
        }
    }

    public @Nullable GutterFileData getData(@NotNull String filePath) {
        return fileDataMap.get(filePath);
    }

    public @NotNull Map<String, GutterFileData> getAllData() {
        return fileDataMap;
    }

    public @NotNull String getScopeDisplayName() {
        return scopeDisplayName;
    }

    public void setScopeDisplayName(@NotNull String name) {
        this.scopeDisplayName = name;
    }

    public void addListener(@NotNull Listener listener) {
        listeners.add(listener);
        LOG.info("GutterDataService.addListener: " + listener.getClass().getSimpleName() + ", total=" + listeners.size());
    }

    public void removeListener(@NotNull Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void dispose() {
        listeners.clear();
        fileDataMap.clear();
    }
}
