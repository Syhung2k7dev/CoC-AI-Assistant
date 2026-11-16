# AELM – Agent Evolution Lifecycle Manager

AELM is the application-side lifecycle coordinator for AI-authored CASL Workflow and Skill artifacts. It is implemented in `AelmLifecycleManager.kt` and uses the existing Gemini authoring services, CADSS-backed registries, and the shared CASL interpreter.

## Implemented lifecycle

1. **Create** – Gemini proposes a new Workflow or Skill as an untrusted JSON document. The authoring layer validates the CASL schema and stores it as Draft.
2. **Revise** – a revision is created as a new immutable version. The previous version is not overwritten. The revised Skill must preserve its ID and increment the version by one.
3. **Test** – AELM executes Draft scripts against a restricted in-process sandbox. The sandbox exposes only deterministic `test.*` APIs; it does not register production memory, Accessibility, scheduler, or device-control APIs. Tests provide inputs and expected output fields.
4. **Release** – release is rejected unless every test case passed and a receipt matches the exact artifact type, ID, version, and SHA-256 digest of the tested program. The registry then performs its TESTING → VERIFIED transition and records test totals/time.

## Test contract

- Between 1 and 25 test cases per test run.
- Each case has a unique bounded ID, JSON inputs, and expected output fields.
- Expected output values are compared recursively; array ordering is significant.
- Workflow tests can call only sandbox APIs and already-VERIFIED, version-pinned Skills.
- Skill tests can call only sandbox APIs and already-VERIFIED, version-compatible dependencies.
- A failed test does not create a release receipt and leaves the artifact in Draft.
- A receipt becomes invalid if the program content changes after testing.

## CADSS integration

Workflow versions remain in `CaslWorkflowRegistry`; Skill manifests and script versions remain in `AgentSkillRegistry`. AELM does not duplicate or replace those registries. Test receipts are stored separately in the local `aelm_test_receipts_v1` preferences namespace and contain only metadata and a program digest, not prompts, API credentials, or test input payloads.

## Safety and execution boundary

AI-generated source code is never evaluated. CASL remains a bounded JSON AST with explicit operations, step/depth/repeat/wait limits, and a named API allow-list. Draft tests cannot invoke real device actions. Production execution still requires the Workflow Engine's user-approval gate and VERIFIED artifacts.

## Verification status

This document describes the implementation added with this change. It is not a claim that CI, APK installation, or device-level execution has passed. Those statuses must be read from the associated GitHub Actions run and physical-device verification separately.
