# Description

<!-- Plugin description -->

## Create custom "scopes" for any target branch. Selectable in a tool window, which is called **GIT SCOPE**.

The Current "scope" is displayed/available as

- diff in the tool window
- "line status" in the "line gutter"
- custom "scope" and finally as a
- status bar widget

### Story

Most developers enjoy using version control to inspect code changes before a commit. However, after committing, all
version control changes and line statuses are lost. Since branches often consist of multiple commits, this plugin makes
those commits intuitively visible again.

### Modifications in Detail

**Change Browser:**

Adds a tool window with a "change browser" (similar to version control) that displays the current diff of the **GIT
SCOPE**.

**Line Status Gutter:**

Adjusts the line status according to your **GIT SCOPE**. Normally this built-in feature shows only the current "HEAD"
changes

READ: https://www.jetbrains.com/help/phpstorm/file-status-highlights.html

**Scope:**

Adds a custom *Scope* (used to do inspections, search/replace, ect), i.e. search results are filtered by **GIT SCOPE**.

READ: https://www.jetbrains.com/help/phpstorm/scopes.html

**Status Bar Widget**

To see the current selection of the Git Scope even when the tool window is not open, you can look at the status bar
widget.

## Shortcuts (Added by this Plugin)

| Shortcut | Description                                      |
|----------|--------------------------------------------------|
| Alt+H    | Toggle between HEAD and last git scope selection |

## More Useful Shortcuts

| Shortcut                                  | Description                    |
|-------------------------------------------|--------------------------------|
| Ctrl+D (on any file in a changes browser) | Open diff window               |
| F7                                        | step forward (in diff window)  |
| Shift+F7                                  | step backward (in diff window) |

<!-- Plugin description end -->

# Changelog

<!-- Plugin changelog -->

### 2023.1.5

# Fixed

- "Only Changes Since Common Ancestor": Improved Git comparison accuracy by using GitHistoryUtils.history() with a
  two-dot syntax (git diff A..B)

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
