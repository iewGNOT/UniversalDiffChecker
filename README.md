# Universal Difference Checker

## Description

Universal Difference Checker (UDC) is a Java 17 / JavaFX desktop tool built to give engineers and analysts a precise, format‑aware way to inspect and merge files without ever altering their original structure. I created UDC because switching between disparate diff utilities made it impossible to review mixed workloads—JSON APIs, XML configs, CSV exports, and raw binaries—in one consistent environment. UDC solves that by streaming each file exactly as it exists on disk into a RichTextFX viewer, tagging the detected format, and only running a diff when both sides are loaded.

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
- [Credits](#credits)
- [License](#license)
- [Badges](#badges)
- [Features](#features)
- [How to Contribute](#how-to-contribute)
- [Tests](#tests)

## Installation

1. Ensure the following prerequisites are installed:
   - Windows 10 or later (JavaFX dependencies use the `win` classifier; adjust for other OSes).
   - JDK 17+
   - Maven 3.9+
2. Clone the repository:
   ```bash
   git clone https://github.com/iewGNOT/UniversalDiffChecker.git
   cd universal-diff-checker
   ```
3. Build the project:
   ```bash
   mvn clean install
   ```

## Usage

1. Launch the JavaFX UI:
   ```bash
   mvn javafx:run
   ```
2. Use the toolbar to select a left or right file. Each file appears immediately in its pane with a “Format: …” badge so you know whether you’re looking at XML, JSON, CSV, plain text, or binary data.
3. After both files are loaded, click **Compare** (enabled only when both sides are present). The streaming diff renderer highlights line‑level or byte‑level differences without reformatting the source.
4. Use the merge controls to apply a “take left/right” strategy and export the result. Binary files show offsets/hex/ASCII, while text panes remain selectable for copy/paste.

> _Tip:_ Add screenshots to `assets/images` and reference them with Markdown syntax:
> ```md
> ![Diff preview](assets/images/diff-preview.png)
> ```

## Credits

- Project lead & primary developer: [Your Name](https://github.com/your-handle)
- Libraries:
  - [RichTextFX](https://github.com/TomasMikula/RichTextFX)
  - [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils)
  - [JaCoCo](https://www.jacoco.org/)

## Badges

![Java](https://img.shields.io/badge/java-17-orange) ![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)

## Features

- Immediate file preview with format detection—no compare button needed for single-file review.
- Streaming diff engine (Myers + RichTextFX chunking) keeps the UI fluid on large files.
- Binary diff view with offset, hex, and ASCII highlighting.
- Merge workflow with configurable strategies and preserved encodings.
- BOM-aware file loading and strict “no normalization” handling for XML/JSON/CSV files.

## How to Contribute

1. Fork the repository and create a feature branch: `git checkout -b feature/your-feature`.
2. Follow the existing code style and include RichTextFX-based UI updates where relevant.
3. Write or update unit tests.
4. Submit a pull request referencing related issues.

Consider adopting the [Contributor Covenant](https://www.contributor-covenant.org/) if you open the project to outside contributors.

## Tests

Run the automated suite (JaCoCo instrumentation included) with:

```bash
mvn clean test
```

This command compiles sources, runs the JUnit 5 tests, and outputs coverage reports to `target/site/jacoco`. Use the results to ensure new features maintain or improve test coverage.
