## [2025.2]

### Fixes

- Fixed additional minor EDT issues
- Improved performance of various operations (scope detection, window population, scope filtering).
  This should improve performance when used with large scopes.

### Added

- Support 2025.3 EAP
- Added [possibility to rename a tab once created](https://github.com/comod/git-scope-pro/issues/54)
- Added [show in project and rollback actions](https://github.com/comod/git-scope-pro/issues/58)
- Added ['Select In' -> 'Git Scope' action](https://github.com/comod/git-scope-pro/issues/59)

## [2025.1.3]

### Fixes

- Fixed [Preview tab should not open editor in "edit-mode"](https://github.com/comod/git-scope-pro/issues/53)
- Fixed [Dissapearing diff-highlighting in 2025.1.2 ](https://github.com/comod/git-scope-pro/issues/52)
- Fixed [Update README.md](https://github.com/comod/git-scope-pro/issues/50)
- Fixed [Still invalid SinceBuild](https://github.com/comod/git-scope-pro/issues/30)
- Improved [Prevent gitscope tool window from auto scrolling to top](https://github.com/comod/git-scope-pro/issues/33)

## [2025.1.2]

### Added

- Plugin back as a Free and Open Source Software

## [2025.1.1]

### Fixes

- Support 2024.3.x - 2025.2.x

## [2025.1]

### Fixes

- Major rework to fix issues with slow operation blocking GUI EDT thread. This aligns to current IntelliJ plugin
  requirements

### Added

- Support for user-defined git references as scopes
- Ensure opened files will retain cursor position
- For multi-repo layouts, display main repo first in list
- Migration to IntelliJ's native diff view with standard gutter popup functionality
- Allow enable/disable of status bar widget (including use themed coloring)
- Save/restore of active git scope tab
- Support preview tabs if enabled in settings
- Always retain scroll position in VCS tree window

## [2023.1.5]

### Fixes

- "Only Changes Since Common Ancestor": Improved Git comparison accuracy by using GitHistoryUtils.history() with a
  two-dot syntax (git diff A..B)

## [2023.1.4]

### Added

- "Only Changes Since Common Ancestor"-Checkbox: Allows to compare changes that were made only on this branch
- VcsContextMenu-Action: Compare any commit with Git Scope

## [2023.1.3]

### Fixes

- @NotNull parameter 'value' of state/MyModelConverter.fromString must not be null

## [2023.1.2]

### Fixes

- Fix compatibility issues

## [2023.1.1]

### Fixes

- Complete overhaul of the plugin
- Cleaner UI
- Tab-based tool window
- HEAD tab is always present by default
- Add new tab to create a new *GitScope*
- Tabs are persisted. This way you can keep "favorites" like a "main" branch tab
- Use the "ToggleHead" action to switch between HEAD and the (last) selected tab (Alt+H)
