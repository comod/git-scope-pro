# Description

<!-- Plugin description -->

## Create custom "scopes" for any target branch. Selectable in a tool window, which is called **GIT SCOPE**.

The Current "scope" is displayed/available as

- diff in the tool window
- "line status" in the "line gutter"
- custom "scope" and finally as a
- status bar widget

<!-- Plugin description end -->

# Changelog

<!-- Plugin changelog -->

### 2023.1.4

# Added

- "Only Changes Since Common Ancestor"-Checkbox: Allows to compare changes that were made only on this branch
- VcsContextMenu-Action: Compare any commit with Git Scope

---

### 2023.1.3

- Bugfix: @NotNull parameter 'value' of state/MyModelConverter.fromString must not be null

### 2023.1.2

- Fix compatibility issues

### 2023.1.1

- Complete overhaul of the plugin
- Cleaner UI
- Tab-based tool window
- HEAD tab is always present by default
- Add new tab to create a new *GitScope*
- Tabs are persisted. This way you can keep "favorites" like a "main" branch tab
- Use the "ToggleHead" action to switch between HEAD and the (last) selected tab (Alt+H)

<!-- Plugin changelog end -->

# Gradle

```bash
gradle  runIde
```

Build (build/distributions)

```bash
gradle build
```
