# GIT SCOPE (Intellij Plugin)

<!-- Plugin description -->

Create custom "scopes" for any target branch. Selectable in a tool window, which is then called **GIT SCOPE**.
The Current "scope" is displayed as a

- diff in the tool window
- "line status" in the "line gutter"
- custom "scope" and finally as a
- status bar widget

### Story

I think every developer loves to check their changes with **version control** before committing.
But there is a big problem after committing the code: All changes in **version control** and also the line status
disappear completely.
Usually a branch contains more than one commit. This plugin helps you to make these commits visible again in an
intuitive way!

### Plugin Features

![](docs/icon.svg) **New Scope:**

To add a new scope, click the "+" tab on the Git Scope panel:
![](docs/add.png)

In the "New*" tab you get a few different options to define the scope:

![](docs/selection.png)

1. Select either a local or remote branch in the current repo. If the repo contains sub-repos, all repos will be listed
   with the main repo being the first repo in the list.
2. Alternatively, you can manually type the branch, tag or git reference and press Enter. A git reference can be any
   valid
   git reference such as `HEAD~2`, `<short hash>`, ...
3. If you want to bind the scope to the common ancestor for `HEAD` and the current selection, you can check the common
   ancestor checkbox.
4. If the list of branches are long, it can be filtered using the search box.

![](docs/icon.svg) **Change Browser:**

Whenever the scope selection is done, the tab will turn into a "change browser" (similar to version control) that
displays the current diff of the **GIT SCOPE**.

![](docs/toolwindow.png)

![](docs/icon.svg) **Line Status Gutter:**

Adjusts the line status according to your **GIT SCOPE**. Normally this built-in feature shows only the current "HEAD"
changes

READ: https://www.jetbrains.com/help/phpstorm/file-status-highlights.html

| HEAD               | "main"-Branch            |
|--------------------|--------------------------|
| ![](docs/head.png) | ![](docs/linestatus.png) |

![](docs/icon.svg) **Scope:**

Adds a custom *Scope* (used to do inspections, search/replace, ect), i.e. search results are filtered by **GIT SCOPE**.

READ: https://www.jetbrains.com/help/phpstorm/scopes.html

![](docs/scope.png)

![](docs/icon.svg) **Status Bar Widget**

To see the current selection of the Git Scope even when the tool window is not open, you can look at the status bar
widget.

![](docs/statusbar.png)

![](docs/icon.svg) **Use Commit as Git Scope**

In the Git panel, you can right-click on any commit and select "Use Commit as Git Scope" to automatically add the
commit as a new Git Scope.

![](docs/usescope.png)

![](docs/icon.svg) **Project Panel Filter**

The Project panel can also be filtered to show only files part of the current Git Scope.

![](docs/projfilter.png)

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

### Notes

The examples above relies on the gutter highlighting feature in Jetbrains IDEs. Make sure that this feature is enabled
via `Tools -> Diff & Merge -> Highlight modified lines in gutter` (IntelliJ).

Previous versions of the plugin disabled this setting, while current versions rely on this setting being enabled to show
changes in gutter.

<!-- Plugin description end -->

# Howto build and debug

Build (build/distributions)

```bash
./gradlew buildPlugin
```

Run/debug plugin

```bash
./gradlew runIde
```

Verify

```bash
./gradlew verifyPlugin
```