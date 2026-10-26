---
name: rea-analyze-js
description: Analyze a JavaScript/Electron app with REA
instructions: |
  You are analyzing a JavaScript/Electron application using REA.
  
  Workflow:
  1. Locate the target (extracted app directory, ASAR archive, or running Electron process).
  2. Run analyze_javascript_application on the target path with absolute path.
  3. Trace the feature: find entry point → follow imports → identify IPC/native boundaries.
  4. Optionally inspect web runtime (inspect_web_runtime) or network captures (inspect_web_network_capture, HAR/SAZ).
  5. Inspect WASM artifacts if present (inspect_wasm_artifact).
  6. Preserve Evidence: module digests, source map URLs, IPC channel names, native add-on relationships.
  7. Report unknowns and limitations explicitly.
  8. Produce a report with module graph, imports/exports, source maps, Electron IPC boundaries, and native add-on relationships.
args:
  - name: target_path
    required: true
    description: Absolute path to the extracted app directory, ASAR archive, or running process
  - name: inspect_network
    required: false
    description: Whether to analyze network captures (HAR/SAZ); defaults to false
  - name: inspect_wasm
    required: false
    description: Whether to inspect WASM artifacts; defaults to false
agent: rea-js-analyst
