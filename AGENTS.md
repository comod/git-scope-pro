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
