# AELM – Agent Evolution Lifecycle Manager

AELM is the lifecycle coordinator for AI-authored CASL Workflow and Skill artifacts. It uses the existing Gemini authoring services, CADSS-backed registries, and the shared CASL interpreter.

## Implemented lifecycle

1. **Create** – Gemini proposes a new Workflow or Skill as untrusted JSON. The authoring layer validates the CASL schema and stores it as Draft.
2. **Revise** – a revision creates a new immutable version; the previous version is not overwritten. A revised Skill preserves its ID and increments the version by one.
3. **Test** – AELM executes Draft scripts against a restricted in-process sandbox. The sandbox provides in-memory `memory.read`, `memory.write`, `memory.append`, deterministic `system.time`, and mock `action.tap`, `action.swipe`, and `action.wait` APIs. These mocks do not invoke Accessibility, WorkManager, or real device actions.
4. **Release** – release is rejected unless every test case passes and a receipt matches the exact artifact type, ID, version, and SHA-256 digest of the tested program. The registry then performs the TESTING → VERIFIED transition.

## Test contract

- Between 1 and 25 test cases per run.
- Each case has a unique bounded ID, JSON inputs, and expected output fields.
- Expected values are compared recursively; array ordering is significant.
- Workflow tests can call only sandbox APIs and already-VERIFIED Skills.
- Skill tests can call only sandbox APIs and already-VERIFIED, version-compatible dependencies.
- A failed test creates no release receipt and leaves the artifact in Draft.
- A receipt is valid only while its SHA-256 matches the exact tested artifact.

## CADSS integration

Workflow versions remain in `CaslWorkflowRegistry`; Skill manifests and script versions remain in `AgentSkillRegistry`. AELM does not duplicate or replace those registries. Test receipts are stored in the CADSS collection `aelm_test_receipts`; they contain metadata and a program digest, not prompts, API credentials, or test input payloads. CADSS backup export/import therefore includes the receipts.

## Safety and execution boundary

AI-generated source code is never evaluated. CASL remains a bounded JSON AST with explicit operations, step/depth/repeat/wait limits, and a named API allow-list. Draft tests cannot invoke real device actions. Production execution still requires the Workflow Engine's user-approval gate and VERIFIED artifacts.

## Cancellation and failure handling

Coroutine cancellation is propagated to the caller and is not converted into an ordinary authoring failure. A failed test does not produce a release receipt. A release attempt also fails if the artifact has changed since its successful test.

## Verification status

This document describes the integrated implementation. It is not a claim that CI, APK installation, or device-level execution has passed. Those statuses must be read from the associated GitHub Actions run and physical-device verification separately.
