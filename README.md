# GIT SCOPE (Intellij Plugin)

<!-- Plugin description -->
Provides a tool window and a status bar widget to select a target branch called **GIT SCOPE**
<ul>
    <li> 1. Adds a tool window with a change browser (similar to Version Control) which shows the current diff of your
    **GIT SCOPE**.
    <li> 2. Adapts the Line Status according to your **GIT SCOPE**. Usually this built-in feature shows only the current
    "HEAD" changes.
    <li> 3. Adds a Custom *Scope* (inspections, search/replaces, ect) "Git Scope (Files)", which means search results
    will be filtered according to **GIT SCOPE**
</ul>
<!-- Plugin description end -->

<!-- Plugin changelog -->

## 2023.1.1

### Changed

- Complete overhaul of the plugin
- Cleaner UI
- Tab-based tool window
    - HEAD tab is always present by default
    - Add new tab to create a new *GitScope*
    - Tabs are persisted. This way you can keep "favorites" like a "main" branch tab
    - Use the "ToggleHead" action to switch between HEAD and the (last) selected tab (Alt+H)

<!-- Plugin changelog end -->