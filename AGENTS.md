# Agent Instructions

## Comment style (Java/Kotlin)

- `/** ... */` (Javadoc) is only for comments directly above a declaration (class, method, field).
  Using it elsewhere trips IntelliJ's "Dangling Javadoc comment" inspection, since there's no
  declaration for it to document.
  - Short, single-sentence: `/** Comment text. */` on one line.
  - Longer, multi-sentence:
    ```
    /**
     * First sentence.
     * Second sentence.
     */
    ```
- `/* ... */` (plain block comment) for multi-line explanations inside method bodies — above a
  statement, not a declaration.
- `//` for single-line asides.

Do not wrap a single continuous comment across several `//` lines — use a block form instead
(`/** */` above a declaration, `/* */` elsewhere).

## Commit messages

- Short and concise. Not a changelog of everything that changed — describe the end result once
  this commit has merged.
- No `Co-Authored-By:` trailer.
- Wrap the summary and body lines to 72 characters.
- If the commit fully resolves a GitHub issue, close it with `Fixes comod/git-scope-pro#NNN` on
  its own line in the body (fully-qualified `owner/repo#NNN`, matching the `upstream` remote —
  see git history for the established usage, e.g. `Fixes comod/git-scope-pro#91`). If the commit
  is only related to an issue without resolving it, reference the number without a closing
  keyword instead.
