## [2025.1.1]
### Fixed

- Support 2024.3.x - 2025.2.x

## [2025.1]

### Fixed

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

### Fixed

- "Only Changes Since Common Ancestor": Improved Git comparison accuracy by using GitHistoryUtils.history() with a
  two-dot syntax (git diff A..B)

## [2023.1.4]

### Added

- "Only Changes Since Common Ancestor"-Checkbox: Allows to compare changes that were made only on this branch
- VcsContextMenu-Action: Compare any commit with Git Scope

## [2023.1.3]

### Fixed

- @NotNull parameter 'value' of state/MyModelConverter.fromString must not be null

## [2023.1.2]

### Fixed

- Fix compatibility issues

## [2023.1.1]

### Fixed

- Complete overhaul of the plugin
- Cleaner UI
- Tab-based tool window
- HEAD tab is always present by default
- Add new tab to create a new *GitScope*
- Tabs are persisted. This way you can keep "favorites" like a "main" branch tab
- Use the "ToggleHead" action to switch between HEAD and the (last) selected tab (Alt+H)
