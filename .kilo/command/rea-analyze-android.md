---
name: rea-analyze-android
description: Analyze an Android APK with REA
instructions: |
  You are analyzing an Android APK using REA.
  
  Workflow:
  1. Obtain the APK (extracted or downloaded).
  2. Run analyze_android_apk on the APK path with absolute path for manifest, classes, decompiled methods, and references.
  3. Run inspect_apk_signature for supply-chain verification (v1/v2/v3/v3.1 signers, cert fingerprints).
  4. For UI/features: run analyze_android_resources for decoded resources and string tables.
  5. For Flutter apps: run analyze_flutter_app for Dart snapshot hash and engine provenance.
  6. For deeper pseudo-code analysis: run analyze_jeb_project (requires JEB MCP endpoint).
  7. For runtime behavior: run analyze_adb_device with the target package.
  8. Preserve Evidence: class/method signatures, resource IDs, certificate chains.
  9. Produce a report with manifest declarations, decompiled code, resource analysis, and signature details.
args:
  - name: apk_path
    required: true
    description: Absolute path to the APK file to analyze
  - name: inspect_signature
    required: false
    description: Whether to verify APK signature; defaults to true
  - name: analyze_resources
    required: false
    description: Whether to analyze Android resources; defaults to true
  - name: adb_device
    required: false
    description: Optional device serial for runtime behavior analysis
agent: rea-android-analyst
