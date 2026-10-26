---
name: rea-investigate-feature
description: Investigate a feature across multiple targets with REA
instructions: |
  You are leading a cross-target feature investigation using REA.
  
  Workflow:
  1. Clarify the feature to investigate and identify the targets involved (native binary, JS/Electron app, Android APK, or combinations).
  2. Invoke the rea-team-lead agent to decompose the investigation into target-specific analysis tasks.
  3. Delegate to specialist agents in parallel: rea-native-analyst, rea-js-analyst, rea-android-analyst as applicable.
  4. Each specialist runs the appropriate REA analysis (analyze_native_binary, analyze_javascript_application, analyze_android_apk, etc.).
  5. Collect Evidence from each target: observations, derivations, unknowns.
  6. Cross-reference findings across targets (e.g., JS IPC -- native handler, Android manifest -- native library).
  7. Synthesize findings into a coherent explanation of how the feature works across targets.
  8. Produce a unified report with an evidence trail.
  9. If requested, guide reconstruction or implementation based on the evidence.
args:
  - name: feature_description
    required: true
    description: High-level description of the feature to investigate
  - name: targets
    required: true
    description: Comma-separated list of targets (native binary path, JS app path, APK path)
  - name: synthesize
    required: false
    description: Whether to produce a unified cross-target report; defaults to true
  - name: guide_reconstruction
    required: false
    description: Whether to guide implementation/reconstruction; defaults to false
agent: rea-team-lead
