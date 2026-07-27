# Plugin Classes to Check in HPROF Analysis

This document lists all plugin classes to search for when analyzing heap dumps to identify memory leaks after plugin
unload. All classes should have **count = 0** after successful plugin unload.

Classes are organized by module: **backend**, **frontend**, and **shared**.

---

## Backend Module

### Services

*Expected: 1 instance per project, or 0 after unload*

- `service.ViewService`
- `service.ToolWindowService`
- `service.ToolWindowServiceInterface` *(interface)*
- `service.StatusBarService`
- `service.GitService`
- `service.TargetBranchService`
- `implementation.compare.ChangesService`

### State/Persistence

- `state.State`
- `state.MyModelConverter`
- `state.WindowPositionTracker`

### Listeners

*Expected: 0 after unload*

- `listener.MyBulkFileListener`
- `listener.MyDynamicPluginListener`
- `listener.MyToolWindowListener`
- `listener.VcsStartup`
- `listener.MyChangeListListener`
- `listener.MyGitRepositoryChangeListener`
- `listener.MyFileEditorManagerListener`
- `listener.MyTabContentListener`
- `listener.MyTreeSelectionListener`
- `listener.ToggleHeadAction`
- `listener.VcsContextMenuAction`

### UI Components

#### Main Components

- `toolwindow.ToolWindowView`
- `toolwindow.ToolWindowUIFactory`
- `toolwindow.BranchSelectView`
- `toolwindow.TabOperations`
- `toolwindow.VcsTreeActions`

#### Actions

- `toolwindow.actions.TabMoveActions`
- `toolwindow.actions.TabMoveActions$MoveTabLeft`
- `toolwindow.actions.TabMoveActions$MoveTabRight`
- `toolwindow.actions.RenameTabAction`
- `toolwindow.actions.ResetTabNameAction`

#### UI Elements

- `toolwindow.elements.VcsTree`
- `toolwindow.elements.BranchTree`
- `toolwindow.elements.BranchTreeEntry`
- `toolwindow.elements.MySimpleChangesBrowser`
- `toolwindow.elements.CurrentBranch`
- `toolwindow.elements.TargetBranch`

### Status Bar

- `statusBar.MyStatusBarWidget`
- `statusBar.MyStatusBarWidgetFactory`
- `statusBar.MyStatusBarPanel`

### Models

- `model.MyModel`
- `model.MyModel$field` *(enum)*
- `model.MyModelBase`
- `model.TargetBranchMap`
- `model.Debounce`

### Implementation Classes

#### Line Status Tracker

- `implementation.lineStatusTracker.MyLineStatusTrackerImpl`

#### Scope

- `implementation.scope.MyScope`
- `implementation.scope.MyPackageSet` *(registered with NamedScopeManager - critical leak if not unregistered)*
- `implementation.scope.MyScopeInTarget`
- `implementation.scope.MyScopeNameSupplier`

#### File Status

- `implementation.fileStatus.GitScopeFileStatusProvider`

### Settings (Backend UI)

- `settings.GitScopeSettingsComponent`
- `settings.GitScopeSettingsConfigurable`

### RPC

- `rpc.BackendGutterRpcImpl`
- `rpc.BackendGutterRpcProvider`

### Utility Classes

- `utils.CustomRollback`
- `utils.GitUtil`
- `utils.PlatformApiReflection`

---

## Frontend Module

### Services

- `gitscope.frontend.GutterRenderingService`
- `gitscope.frontend.GutterRenderingStartup`

### Gutter Rendering

- `implementation.gutter.LineStatusGutterMarkerRenderer`
- `implementation.gutter.ScopeLineStatusMarkerRenderer`
- `implementation.gutter.ScopeDiffViewer`
- `implementation.gutter.ScopeGutterHighlighterManager`
- `implementation.gutter.ScopeGutterPopupPanel`

### RPC

- `rpc.FrontendGutterSubscriptions`
- `rpc.FrontendGutterSubscriptionsStartup`

---

## Shared Module

### Services

- `service.GutterDataService`
- `service.GutterDataService$GutterFileData`
- `service.GutterDataService$Listener` *(interface)*

### Gutter Data Model

- `implementation.gutter.Range`
- `implementation.gutter.RangesBuilder`

### RPC Interface & DTOs

- `rpc.GutterRpcApi`
- `rpc.GutterUpdateEvent`
- `rpc.GutterUpdateEvent$DataUpdated`
- `rpc.GutterUpdateEvent$DataCleared`
- `rpc.GutterUpdateEvent$AllCleared`
- `rpc.GutterFileDataDto`
- `rpc.GutterRangeDto`

### Settings

- `settings.GitScopeSettings`

### System

- `system.Defs`

### Utility Classes

- `utils.SharedReflection`
- `utils.Notification`

---

## Anonymous/Inner Classes to Look For

*These are patterns - search for classes matching these names:*

- `ToolWindowView$listener` *(Consumer<MyModel.field> stored as field)*
- `ViewService$modelListeners` *(HashMap of model → Consumer listeners)*
- `MyLineStatusTrackerImpl$$Lambda` *(lambdas from line status tracker)*
- `ScopeLineStatusMarkerRenderer$$Lambda` *(lambdas from gutter renderer)*
- `GutterRenderingService$$Lambda` *(lambdas from rendering service)*
- `BackendGutterRpcImpl$$Lambda` *(callbackFlow listener)*
- `MySimpleChangesBrowser$1` *(anonymous MouseAdapter)*
- `BranchTree$MyColoredTreeCellRenderer`
- Any class ending with `$$Lambda$...`

---

## How to Search Efficiently

### 1. Search by Package Prefix

Filter the HPROF classes view using these prefixes:

- `service.`
- `listener.`
- `toolwindow.`
- `implementation.`
- `model.`
- `state.`
- `statusBar.`
- `settings.`
- `utils.`
- `rpc.`
- `gitscope.frontend.`
- `system.`

### 2. Filter the Classes View

1. Sort by "Count" column
2. Look for `count != 0`
3. Focus on YOUR packages (ignore `com.intellij.*`, `java.*`, `kotlin.*`)

### 3. Priority Classes to Check

*Most likely to leak:*

1. **All listeners** - Must be unregistered
2. **ToolWindowView** - UI components must be disposed, model listener removed
3. **ViewService** - Model listeners must be removed in dispose()
4. **MyLineStatusTrackerImpl** - Background tasks must be cancelled
5. **ScopeLineStatusMarkerRenderer** - Highlighters and mouse listeners must be removed
6. **GutterRenderingService** - Must remove itself from GutterDataService listeners
7. **BackendGutterRpcImpl** - callbackFlow listener removed on awaitClose
8. **FrontendGutterSubscriptions** - Coroutine scope cancelled on service dispose
9. **GutterDataService** - Listener list and file data map cleared on dispose
10. **Any class with `$` in the name** - Anonymous/inner classes often capture outer references

---

## Analysis Steps

1. Open the `.hprof` file in IntelliJ's memory profiler
2. Navigate to the "Classes" view
3. Sort by "Count" column in descending order
4. Search for each class using the package prefixes above
5. **Report back any classes with `count != 0`** and we'll fix them!
