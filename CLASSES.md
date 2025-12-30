# Plugin Classes to Check in HPROF Analysis

This document lists all plugin classes to search for when analyzing heap dumps to identify memory leaks after plugin unload. All classes should have **count = 0** after successful plugin unload.

## Services
*Expected: 1 instance per project, or 0 after unload*

- `service.ViewService`
- `service.ToolWindowService`
- `service.ToolWindowServiceInterface` *(interface - unlikely to leak but check if implemented by plugin classes)*
- `service.StatusBarService`
- `service.GitService`
- `service.TargetBranchService`
- `implementation.compare.ChangesService`

## State/Persistence

- `state.State`
- `state.MyModelConverter`
- `state.WindowPositionTracker`

## Listeners
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

## UI Components

### Main Components
- `toolwindow.ToolWindowView`
- `toolwindow.ToolWindowUIFactory`
- `toolwindow.BranchSelectView`
- `toolwindow.TabOperations`
- `toolwindow.TabMoveActions`
- `toolwindow.TabMoveActions$MoveTabLeft`
- `toolwindow.TabMoveActions$MoveTabRight`
- `toolwindow.VcsTreeActions`

### UI Elements
- `toolwindow.elements.VcsTree`
- `toolwindow.elements.BranchTree`
- `toolwindow.elements.BranchTreeEntry`
- `toolwindow.elements.MySimpleChangesBrowser`
- `toolwindow.elements.CurrentBranch`
- `toolwindow.elements.TargetBranch`

## Status Bar

- `statusBar.MyStatusBarWidget`
- `statusBar.MyStatusBarWidgetFactory`
- `statusBar.MyStatusBarPanel`

## Models

- `model.MyModel`
- `model.MyModel$field` *(enum - check for RxJava subscription leaks)*
- `model.MyModelBase`
- `model.TargetBranchMap`
- `model.Debounce`

## Implementation Classes

### Line Status Tracker
- `implementation.lineStatusTracker.MyLineStatusTrackerImpl`
- `implementation.lineStatusTracker.CommitDiffWorkaround`

### Scope
- `implementation.scope.MyScope`
- `implementation.scope.MyPackageSet` *(registered with NamedScopeManager - critical leak if not unregistered)*
- `implementation.scope.MyScopeInTarget`
- `implementation.scope.MyScopeNameSupplier`

### File Status
- `implementation.fileStatus.GitScopeFileStatusProvider`

## Utility Classes

- `utils.CustomRollback`
- `utils.GitCommitReflection`
- `utils.GitUtil`
- `utils.Notification`
- `system.Defs`

## Anonymous/Inner Classes to Look For

*These are patterns - search for classes matching these names:*

- `TabOperations$1` *(rename action)*
- `TabOperations$2` *(reset action)*
- `TabOperations$3` *(move left action)*
- `TabOperations$4` *(move right action)*
- `VcsTree$$Lambda` *(any lambda from VcsTree)*
- `MyLineStatusTrackerImpl$1` *(BaseRevisionSwitcher anonymous inner class - circular reference)*
- `MyLineStatusTrackerImpl$$Lambda` *(lambdas from line status tracker)*
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
- `utils.`

### 2. Filter the Classes View
1. Sort by "Count" column
2. Look for `count != 0`
3. Focus on YOUR packages (ignore `com.intellij.*`, `java.*`, `kotlin.*`)

### 3. Priority Classes to Check
*Most likely to leak:*

1. **All listeners** - Must be unregistered
2. **TabOperations and its anonymous classes** - Actions must be unregistered
3. **ToolWindowView** - UI components must be disposed
4. **ViewService** - RxJava subscriptions must be disposed
5. **MyLineStatusTrackerImpl** - Background tasks must be cancelled
6. **Any class with `$` in the name** - Anonymous/inner classes often capture outer references

---

## Analysis Steps

1. Open the `.hprof` file in IntelliJ's memory profiler
2. Navigate to the "Classes" view
3. Sort by "Count" column in descending order
4. Search for each class using the package prefixes above
5. **Report back any classes with `count != 0`** and we'll fix them!
