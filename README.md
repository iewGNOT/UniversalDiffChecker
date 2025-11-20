# Universal Difference Checker (UDC)

Universal Difference Checker is a Java 17 / JavaFX desktop application for engineers, analysts, and QA teams who need a consistent environment to inspect, diff, and merge files ranging from plain text to binary payloads. UDC focuses on accuracy, performance, and transparency—files are shown exactly as stored on disk, and structured formats (XML, JSON, CSV) are never reformatted before display.

## Key Capabilities
- **Immediate single‑pane preview** – Load either side and UDC streams the raw file straight into a RichTextFX `StyledTextArea`, tagging the detected format (XML, JSON, CSV, Text, Binary, Unknown). No diff is required to inspect content.
- **High‑volume diff engine** – Once both files are loaded, the app leverages a streaming diff pipeline (Myers line diff + chunked rendering) to keep the UI responsive even on very large inputs.
- **Binary awareness** – BIN/HEX assets show offsets, hex bytes, and ASCII side‑by‑side with highlighting for differing cells.
- **Guided merge workflow** – Choose merge strategies (take‑left, take‑right, manual) per run, and export merged output while preserving the original encoding.
- **Format auto‑detection** – A combined extension/content heuristic reports the most likely format so reviewers understand what they are looking at before diffing.

## Getting Started

### Prerequisites
- Windows 10 or later (JavaFX dependencies use the `win` classifier – adjust classifiers for other platforms)
- JDK 17 or newer
- Maven 3.9 or newer

### Build & Run
```bash
mvn clean install
mvn javafx:run
```
`mvn javafx:run` launches the desktop UI. Use the toolbar to load left/right files. A single file immediately appears in the associated pane with its format badge. Selecting the second file automatically switches into diff mode; use the `Compare` button if you need to regenerate the diff after toggling options such as JSON key‑order handling.

### Testing & Coverage
```bash
mvn clean test         # executes JUnit suites with JaCoCo attached
mvn verify             # runs tests + generates HTML/XML coverage under target/site/jacoco
```
JaCoCo thresholds (80 % line / 70 % branch) are enforced during `mvn verify`. Open `target/site/jacoco/index.html` to review coverage drill‑downs.

## Project Structure
- `src/main/java/com/universaldiff/app` – JavaFX UI shell (toolbars, RichTextFX panes, streaming renderer).
- `src/main/java/com/universaldiff/core` – Comparison/merge services, file loading, format detection, data models.
- `src/main/java/com/universaldiff/format` – Pluggable adapters for TXT, BIN/HEX, and other formats. XML/JSON/CSV routes currently point to the TXT adapter to avoid normalization.
- `src/test/java` – Unit tests for adapters, detectors, and service wiring (extend with regression suites as needed).

## Extending UDC
- Implement the `FormatAdapter` SPI to introduce additional formats or bespoke handling. Register adapters via `ComparisonService.createDefault(...)` or provide your own service factory.
- `DiffViewModel` exposes properties for file paths, options, and diff hunks; bind additional UI controls or persistence features (e.g., MRU lists, recipe saves) through this layer.
- RichTextFX rendering is centralized in `UniversalDiffApp`. Add custom styles or annotations by extending the chunk appenders or StyleSpan builders.

## Known Limitations
- Manual merge decisions still accept plain text; richer editors for XML/JSON trees are planned.
- CSV heuristics assume consistent headers across both files.
- Multi‑hundred‑MB binaries are rendered via chunked streaming, but additional optimization (memory‑mapped IO) is on the roadmap.
- JavaFX dependencies are configured for Windows only; adjust classifiers to run on Linux/macOS.

## Roadmap Highlights
1. Multi‑pane merge decisions with per‑hunk “take left/right” toggles.
2. Configurable rules for whitespace/number equivalence in text formats.
3. Memory‑mapped binary renderer for extremely large binaries.
4. Session persistence (recent files, comparison presets).
5. Integration tests against a curated corpus of structured and binary files.

Contributions are welcome—open issues or pull requests with clear reproduction steps and screenshots where applicable.
