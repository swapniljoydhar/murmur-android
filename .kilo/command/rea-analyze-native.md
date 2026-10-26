---
name: rea-analyze-native
description: Analyze a native binary with REA
instructions: |
  You are analyzing a native binary using REA.
  
  Workflow:
  1. Identify the target binary format and architecture (ELF, PE, Mach-O, raw).
  2. Select the appropriate REA provider (Ghidra preferred for depth, Hopper for macOS, IDA if licensed).
  3. Run analyze_native_binary on the target path with absolute path.
  4. Optionally run format-specific inspections: inspect_elf_layout, inspect_pe_certificate, inspect_elf_notes.
  5. For language-specific binaries: analyze_go_binary, analyze_rust_binary, analyze_gpu_code_object, analyze_model_artifact, analyze_il2cpp_metadata, analyze_godot_pck.
  6. Preserve Evidence: observations (direct tool output), derivations (your reasoning), and unknowns.
  7. Report limitations explicitly (e.g., missing symbols, stripped binaries).
  8. Produce a report with pseudocode, assembly, strings, symbols, and cross-references.
args:
  - name: binary_path
    required: true
    description: Absolute path to the native binary to analyze
  - name: provider
    required: false
    description: REA provider to use (ghidra, hopper, ida); defaults to ghidra
  - name: format_specific
    required: false
    description: Whether to run format-specific inspections; defaults to true
agent: rea-native-analyst
