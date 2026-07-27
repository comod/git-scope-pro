# Remote Development Refactoring

This document describes the plugin's modular architecture for JetBrains Remote Development
(Split Mode), following the official IntelliJ Platform Modular Plugin guidelines.

## Project Structure

```
git-scope-pro/
├── src/main/resources/META-INF/plugin.xml   ← root descriptor (metadata + <content> only)
├── shared/                                   ← loaded everywhere (backend + frontend)
├── backend/                                  ← loaded on backend (or monolithic IDE)
├── frontend/                                 ← loaded on frontend (or monolithic IDE)
├── build.gradle.kts                          ← root build (pluginModule references)
└── settings.gradle.kts                       ← includes shared, backend, frontend
```

## Split Mode Compliance

The plugin follows the JetBrains modular plugin guidelines:

- **Module descriptors** in `src/main/resources/` (not META-INF): `gitscope.shared.xml`, `gitscope.backend.xml`,
  `gitscope.frontend.xml`
- **Root plugin.xml** contains only `<content>` module declarations — no extensions, actions, or listeners
- **Backend XML** declares dependencies on `intellij.platform.backend`, `intellij.platform.kernel.backend`,
  `gitscope.shared`, `Git4Idea`
- **Frontend XML** declares dependencies on `intellij.platform.frontend`, `gitscope.shared`
- **Shared XML** declares dependency on `intellij.platform.rpc`
- **Content modules** use `required-if-available` for strict loading validation
- **`splitMode = true`** and **`pluginInstallationTarget = BOTH`** declared at `intellijPlatform` level
- **`rpc` compiler plugin** (fleet rpc-compiler-plugin 2.3.20-0.1) applied to all modules
- **No external runtime dependencies** in any module (classloader isolation safe)

## RPC Communication

### Interface (`shared/src/main/java/rpc/GutterRpcApi.kt`)

```kotlin
@Rpc
interface GutterRpcApi : RemoteApi<Unit> {
    suspend fun getGutterUpdates(projectId: ProjectId): Flow<GutterUpdateEvent>
}
```

`GutterUpdateEvent` is a `@Serializable` sealed class with variants: `DataUpdated`, `DataCleared`, `AllCleared`.

### Backend (`backend/src/main/java/rpc/BackendGutterRpcImpl.kt`)

- Implements `GutterRpcApi` using `callbackFlow`
- Registers listener on `GutterDataService` BEFORE replaying existing data (no race condition)
- Registered via `com.intellij.platform.rpc.backend.remoteApiProvider` extension point

### Frontend (`frontend/src/main/java/rpc/FrontendGutterListeners.kt`)

- Subscribes with `durable {}` for automatic reconnection on network errors
- Only activates in split mode (`!IdeProductMode.isMonolith`) to avoid feedback loops
- Publishes received data to the frontend's local `GutterDataService`

### DTOs (`shared/src/main/java/rpc/GutterTopics.kt`)

`@Serializable` data classes: `GutterFileDataDto`, `GutterRangeDto` — contain file path, line ranges, base/head content,
scope display name, and settings state.

## Data Flow

### Monolithic IDE

```
MyLineStatusTrackerImpl → GutterDataService.publish()
  → GutterRenderingService (direct listener, same JVM)
```

RPC subscription is skipped. Frontend gets data directly from the shared `GutterDataService`.

### Split Mode (Remote Development)

```
Backend JVM:
  MyLineStatusTrackerImpl → GutterDataService.publish()
    → BackendGutterRpcImpl (callbackFlow listener) → Flow<GutterUpdateEvent> ── RPC ──→

Frontend JVM:
  ← durable { GutterRpcApi.getInstance().getGutterUpdates() }
    → FrontendGutterSubscriptions → GutterDataService.publish()
      → GutterRenderingService → ScopeLineStatusMarkerRenderer → paint()
```

On subscription (or reconnection), the backend replays all current gutter data so the frontend never misses state.

## Dependency Changes

| Dependency                      | Before           | After                    | Reason                                                                                            |
|---------------------------------|------------------|--------------------------|---------------------------------------------------------------------------------------------------|
| RxJava (`io.reactivex.rxjava3`) | `implementation` | **Removed**              | Replaced with `CopyOnWriteArrayList` listener pattern. Module classloaders can't see `lib/` JARs. |
| Gson (`com.google.code.gson`)   | `implementation` | `compileOnly`            | Platform bundles Gson; only needed for compilation of `MyModelConverter`.                         |
| `rpc` compiler plugin           | —                | Applied to all modules   | Required for `@Rpc` interface codegen (`remoteApiDescriptor`).                                    |
| `intellij.platform.rpc`         | —                | Shared module dependency | Provides `RemoteApi`, `@Rpc`, `RemoteApiProviderService` classes.                                 |

## Startup Initialization

`ViewService.initLater()` handles deterministic tab activation:

1. `initTabsSequentially()` creates all tabs (HEAD + saved models)
2. `tabInitializationInProgress = true` — suppresses `MyTabContentListener`
3. `selectTabByIndex(savedTabIndex)` — selects the saved tab without side effects
4. `tabInitializationInProgress = false`
5. `setActiveModel()` — fires exactly once, triggers `collectChanges()` for the correct scope

This prevents the generation counter invalidation that occurred when `selectTabByIndex` triggered the listener, causing
a double `setActiveModel()` → double `incrementUpdate()` → first `collectChanges` result discarded.

## Gutter Popup Behavior

The gutter diff popup (`ScopeGutterPopupPanel`) closes on:

- **Mouse wheel scroll** — `MouseWheelListener` on editor scroll pane
- **Caret movement** — `CaretListener` (covers keyboard scrolling: arrows, Page Up/Down)
- **Mouse click in editor** — existing `EditorMouseListener`

The popup stays open during **prev/next change navigation** — a `suppressCaretCancel` flag is set during programmatic
`moveToLogicalPosition` + `scrollToCaret` calls.

## File Moves

All existing source files moved from `src/main/java/` into subprojects. No packages renamed.

### → `backend/src/main/java/`

All VCS, service, model, listener, toolwindow, settings, state, and statusBar packages.

### → `frontend/src/main/java/`

Gutter rendering: `ScopeDiffViewer.kt`, `ScopeGutterHighlighterManager.kt`, `ScopeGutterPopupPanel.kt`,
`ScopeLineStatusMarkerRenderer.kt`, `LineStatusGutterMarkerRenderer.kt`.

### → `shared/src/main/java/`

`Range.kt`, `RangesBuilder.kt`, `GitScopeSettings.java`, `Defs.java`, `Notification.java`.

## New Files

| File                                                                                  | Purpose                                                                            |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `shared/src/main/java/service/GutterDataService.java`                                 | In-process bridge: holds per-file gutter data, listener pattern for pub/sub        |
| `shared/src/main/java/rpc/GutterRpcApi.kt`                                            | `@Rpc` interface for cross-process gutter data streaming                           |
| `shared/src/main/java/rpc/GutterTopics.kt`                                            | `@Serializable` DTOs for RPC transport                                             |
| `shared/src/main/java/utils/SharedReflection.java`                                    | Reflection bridge for `getGutterArea()` (internal API)                             |
| `backend/src/main/java/rpc/BackendGutterRpcImpl.kt`                                   | RPC implementation + `RemoteApiProvider` registration                              |
| `backend/src/main/java/implementation/lineStatusTracker/MyLineStatusTrackerImpl.java` | Rewritten: computes ranges on background threads, publishes to `GutterDataService` |
| `frontend/src/main/java/gitscope/frontend/GutterRenderingService.java`                | Manages renderers per document, listens to `GutterDataService`                     |
| `frontend/src/main/java/gitscope/frontend/GutterRenderingStartup.java`                | Eager init of `GutterRenderingService`                                             |
| `frontend/src/main/java/rpc/FrontendGutterListeners.kt`                               | RPC subscription with `durable {}`, publishes to frontend `GutterDataService`      |

## Module Dependencies (Gradle)

```
root build.gradle.kts:
  pluginModule(implementation(project(":shared")))
  pluginModule(implementation(project(":backend")))
  pluginModule(implementation(project(":frontend")))

backend/build.gradle.kts:
  intellijPlatform: intellijIdea, Git4Idea, intellij.platform.backend, kernel.backend, rpc.backend
  implementation(project(":shared"))
  compileOnly("com.google.code.gson:gson:2.13.2")

frontend/build.gradle.kts:
  intellijPlatform: intellijIdea, intellij.platform.frontend
  implementation(project(":shared"))

shared/build.gradle.kts:
  intellijPlatform: intellijIdea, intellij.platform.rpc
```

All modules apply: `org.jetbrains.intellij.platform.module`, `java`, `org.jetbrains.kotlin.jvm`,
`org.jetbrains.kotlin.plugin.serialization`, `rpc`.
