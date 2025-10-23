# Universal Difference Checker (UDC)

Universal Difference Checker is a Java 17 / JavaFX desktop application designed for semantic diff and guided merge workflows across TXT, BIN/HEX, CSV, JSON, and XML files. It targets engineers, students, and analysts who need a single environment to compare, review, and merge structured and binary assets without format-hopping.

## Features (Release 1.0 Scope)
- File type auto-detect with manual override hooks via the view model.
- Format-specific normalization:
  - TXT: whitespace harmonization and per-line comparison.
  - BIN/HEX: byte-level diff with offset reporting.
  - CSV: row comparison with header-aware keys and export back to CSV.
  - JSON: canonical JSON pointer diff with optional key-order ignore.
  - XML: XPath-driven diff for elements, attributes, and text nodes.
- Two-pane preview UI with diff list, detail viewer, and merge action using configurable merge strategy.
- Export pipeline that saves merged output in the source encoding and produces diff-friendly summaries.

## Project Structure
`
src/main/java
universaldiff.app               # JavaFX entry point and UI composition
com.universaldiff.core          # Comparison service, detection, IO helpers
com.universaldiff.format        # Format adapters (TXT, BIN, CSV, JSON, XML)
com.universaldiff.ui            # View models powering the UI
`

## Getting Started
### Prerequisites
- Windows 7 or later
- Java Development Kit (JDK) 17+
- Maven 3.9+

JavaFX dependencies are declared with the Windows classifier (win). Running the app on other platforms will require adjusting the Maven javafx dependency classifiers.

### Build & Run
`
mvn clean install
mvn javafx:run
`

The javafx:run goal launches the Universal Difference Checker UI. Use the toolbar buttons to load left/right files, toggle JSON key-order normalization, and trigger diff/merge operations.

### Testing
Placeholder unit test scaffolding is ready under src/test/java. Add format-specific regression suites as adapters evolve.

## Extending UDC
- Adapters implement the FormatAdapter SPI. New formats can be registered by implementing normalization, diff, and merge behavior and adding them to the FormatAdapterRegistry.
- ComparisonService.createDefault(boolean ignoreJsonKeyOrder) centralizes adapter wiring. Override or extend this factory to plug in additional capabilities or alternate heuristics.
- The UI layer uses a simple DiffViewModel; for richer merge workflows (per-hunk selection, manual editing), extend the view model and bind new controls.

## Known Limitations
- Manual merge decisions for JSON/BIN/XML require textual input; dedicated editors are not yet integrated.
- CSV header detection is heuristic; provide consistent headers for best results.
- XML merge handling focuses on text and attribute updates. Structural inserts/deletes are applied whole-node.
- Large files load into memory; future work should introduce streaming or chunked diff strategies per RI-1.

## Roadmap
1. Add persistence for comparison recipes and recent files.
2. Introduce per-hunk merge selection UI with previews.
3. Expand normalization rules (numeric equivalence, XML whitespace controls, CSV escape edge cases).
4. Harden performance for multi-hundred MB binaries via mapped buffering.
5. Add integration tests against the gold-standard corpus defined in the SRS.
