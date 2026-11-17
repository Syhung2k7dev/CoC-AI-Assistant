# AII & SNM – Agent Interaction Intelligence and Screen Navigation Memory

> Architecture extension for the existing Script-Driven CoC Agent. This document extends, rather than replaces, `ARCHITECTURE_SKILLS_HVCS.md`, `KIEN_TRUC_CADSS_CASL.md`, and `VISION_MEMORY.md`.

## 1. Purpose and boundaries

This extension makes the Agent environment-aware. It defines how the Agent identifies the current UI state, remembers screens and transitions, plans a route between states, selects an appropriate data source, expresses a plan as CASL, and verifies execution.

It does not introduce a second AI Core, a second Workflow Engine, or a competing scripting language.

- **AI Core** reasons through the configured provider adapter. It does not call Android APIs directly.
- **AII (Agent Interaction Intelligence)** interprets goals and context, resolves available knowledge, and produces a constrained execution plan.
- **Vision Engine / HVCS** observes the permitted screen capture and extracts visual and spatial evidence.
- **SNM (Screen Navigation Memory)** stores screen identities, state variants, elements, transitions, and verified paths.
- **CASL** represents executable logic for both Workflows and Skills.
- **Workflow Engine / Script Runtime / Skill Runtime** schedule, orchestrate, and interpret CASL.
- **Action Runtime** is the controlled boundary to registered Android, data, and integration actions.
- **CADSS and Vision Memory** remain the canonical structured-data and visual-asset stores.

A discovered screen or transition is knowledge, not permission. User-approved scope, Android permissions, platform restrictions, and runtime governance remain authoritative.

## 2. Architecture and responsibility

### 2.1 AII

AII is composed of logical services:

| Service | Responsibility |
|---|---|
| Goal Interpreter | Convert the user's request into a typed goal and constraints |
| Context Analyzer | Resolve current app, screen, state, device and execution context |
| Environment Resolver | Match observations to known environments and screen candidates |
| Data Resolver | Select permitted context, CADSS, database, API or visual sources |
| Navigation Planner | Find a valid path through the SNM graph |
| Action Planner | Select registered Skills and primitive actions |
| Plan Validator | Check permissions, dependencies, preconditions and resource limits |
| Result Evaluator | Compare observed results with expected postconditions |

AII returns a structured plan. It does not execute the plan itself. Execution is delegated to CASL and the existing runtimes.

### 2.2 SNM

SNM is a versioned graph-backed knowledge model:

- Screen nodes represent stable screen identities.
- Screen-state variants represent meaningful dynamic states such as loading, modal-open, signed-out or disconnected.
- Element records represent logical UI elements and their observed spatial locations.
- Transition edges represent verified or candidate state changes.
- Navigation paths are derived routes, not independent truth; they must be revalidated against current edge versions.

SNM is not a screenshot gallery and must not treat pHash equality as proof of screen identity.

### 2.3 Unified Action Runtime

The Action Runtime provides one registered and permission-checked dispatch boundary for primitive capabilities:

- observation and visual analysis;
- UI interaction exposed by the permitted Android Accessibility APIs;
- input and navigation;
- wait and state observation;
- computation and data transformation;
- approved database operations;
- registered API calls;
- CADSS/Vision Memory access;
- Skill and Workflow invocation.

CASL scripts may reference only registered action identifiers. They cannot create adapters, bypass permission checks, execute arbitrary code, or access arbitrary databases.

## 3. Observation and coordinate intelligence

### 3.1 Observation pipeline

1. Acquire a screenshot or UI hierarchy through an enabled, permitted Android service.
2. Record source dimensions, orientation, insets, capture timestamp and source identifier.
3. Preprocess a bounded copy for analysis; never mutate the original evidence asset.
4. Run local OpenCV/OCR/UI-hierarchy recognition where available.
5. Use a configured AI vision provider only when semantic interpretation is needed and the applicable sharing policy permits it.
6. Extract candidate elements, text, bounding boxes and spatial relationships.
7. Resolve screen identity and state variant.
8. Store evidence references and confidence values in Vision Memory/CADSS.

The pipeline must support partial results. Missing OCR or hierarchy data must not be fabricated.

### 3.2 Coordinate spaces

Store and explicitly identify three coordinate spaces:

| Space | Definition | Use |
|---|---|---|
| Pixel | Coordinates in the captured source frame | Evidence and frame-local geometry |
| Normalized | 0..1000 relative to a declared content viewport | Resolution-independent representation |
| Relative | Position relative to a parent element or layout anchor | Layout relationships |

Every coordinate record must identify its coordinate-space version, source frame, viewport bounds, dimensions, orientation and timestamp. Conversion must account for crop offsets, letterboxing, system insets and rotation. A normalized point alone is never sufficient to authorize an interaction.

Immediately before an interaction, the runtime must resolve the target again against a fresh observation, validate bounds and permissions, and verify the resulting state. Stale coordinates are invalidated when the source frame or layout profile changes.

### 3.3 Adaptive Grid

Grid is an optional diagnostic overlay, not the primary coordinate model.

- Default: hidden.
- On-demand: rendered only when requested or needed for diagnosis.
- Diagnostic: enabled for a bounded troubleshooting session.

Grid overlays must be separate from the original screenshot and must be reproducible from the source frame and grid parameters. Grid cells may reference bounding boxes and element IDs, but do not replace semantic element recognition.

### 3.4 Confidence

Keep separate confidence values for screen identity, state, element identity, OCR, geometry and transition. Confidence values must include the recognizer/profile version and evidence reference. Low confidence causes re-observation, safe pause or a request for user input; it must not silently degrade into a guessed action.

## 4. SNM data model

The following are logical CADSS entities. They are not a mandate to create a separate database per entity.

### 4.1 Screen

| Field | Meaning |
|---|---|
| screenId | Stable identifier |
| environmentId / applicationId | Owning environment |
| canonicalName | Human-readable label |
| screenType | Screen classification |
| identityFeatures | Versioned visual and structural features |
| layoutProfileId | Layout profile reference |
| stateSignature | State-specific evidence signature |
| elementIds | Related element references |
| evidenceRefs | Vision Memory assets and observations |
| schemaVersion / entityVersion | Data and entity versions |
| lifecycleStatus | Candidate, observed, verified or deprecated |
| confidence | Recognition confidence with provenance |
| firstSeenAt / lastSeenAt | Observation timestamps |

### 4.2 Screen state variant

A state variant is linked to a Screen and describes a meaningful condition without creating a new Screen for every transient visual change.

Examples include loading, modal-open, modal-closed, selected-tab, signed-out and offline. State signatures should use stable text, hierarchy, element presence and visual features where available.

### 4.3 UI element

| Field | Meaning |
|---|---|
| elementId | Stable logical identifier |
| screenId | Owning screen |
| semanticRole | Button, tab, text, input, dialog, panel, etc. |
| semanticLabel | Recognized label |
| identityFeatures | Text, resource/accessibility ID, icon or visual features |
| geometry | Bounding box and coordinate-space metadata |
| parentElementId | Optional hierarchy relationship |
| interactionCapabilities | Observed/declared capabilities |
| evidenceRefs | Source frame and crop references |
| lifecycleStatus | Candidate, observed, verified or deprecated |

Element identity and element position are separate. Moving a button does not automatically create a new logical element.

### 4.4 Transition

| Field | Meaning |
|---|---|
| transitionId | Stable identifier |
| sourceScreenId / targetScreenId | Directed graph endpoints |
| sourceState / targetState | Optional state variants |
| triggerRef | Element or registered external event |
| actionDefinitionId | Registered action or Skill reference |
| preconditions | Required state and permission conditions |
| expectedPostconditions | Observable success conditions |
| verificationRule | Deterministic or evidence-based verification |
| successCount / failureCount | Observed outcomes |
| confidence | Derived reliability, not an assertion |
| evidenceRefs | Execution and observation evidence |
| version / lifecycleStatus | Version and Candidate/Verified/Deprecated status |

A transition is Verified only after an actual permitted execution and a successful postcondition check. AI prediction alone cannot promote it.

### 4.5 Navigation path

A path contains an ordered list of transition IDs and pinned versions, its start/target screen and state, estimated cost, constraints, and validation timestamp. Paths are invalidated or re-planned when any referenced transition version becomes deprecated or changes incompatibly.

## 5. Navigation planning

The planner searches only transitions compatible with the current environment, state, permissions and version constraints.

Supported strategies:

- BFS for unweighted shortest paths;
- Dijkstra for weighted paths;
- A* when a valid heuristic is available;
- dynamic replanning after a verified state divergence.

The configurable path cost may include action count, expected duration, transition confidence, failure history, reversibility and policy risk. The lowest-cost route is not necessarily the route with the fewest clicks.

Before every edge:
1. Re-identify the current screen and state.
2. Check transition preconditions and version.
3. Resolve the target element from current evidence.
4. Validate action permission and bounds.
5. Execute through Action Runtime.
6. Observe the resulting state.
7. Verify the expected postcondition.
8. Record evidence and update transition statistics.

On mismatch, stop the affected path, reconcile the actual state and request a new plan. Never continue using the remaining path as if the failed transition had succeeded.

## 6. AII planning and CASL integration

### 6.1 Plan contract

AII emits a versioned plan containing:

- goal ID and constraints;
- initial and target environment/screen/state;
- assumptions and evidence references;
- ordered steps and dependencies;
- registered Skill/action identifiers;
- input/output contracts;
- preconditions and postconditions;
- permission requirements;
- timeout and resource budgets;
- verification strategy;
- failure branches and recovery boundaries.

### 6.2 Plan-to-script pipeline

Goal → context resolution → SNM/data retrieval → route and action planning → CASL document generation → schema and policy validation → Workflow/Skill runtime → Action Runtime → observation and verification.

The plan is not executable until the CASL validator confirms schema compatibility, registered dependencies, permission scope and resource limits.

### 6.3 CASL requirements

Extend the existing CASL DSL rather than creating a second language. The existing operation set and allow-list model remain authoritative. Additions should be versioned and include:

- typed navigation and screen references;
- action invocation with declared input/output schemas;
- explicit precondition and postcondition assertions;
- bounded wait-until behavior;
- structured error handling and recovery branches;
- checkpoint metadata and execution correlation;
- version-pinned Skill, Workflow and Transition dependencies.

No eval, arbitrary reflection, shell, arbitrary SQL or dynamically loaded executable code. A CASL plan may request a registered database operation but cannot issue unrestricted database commands.

### 6.4 Runtime context and recovery

Each run owns an isolated execution context containing goal, environment, current screen/state, variables, permission grants, script and dependency versions, completed action IDs, pending actions, checkpoint and cancellation state.

Checkpoint recovery must be idempotency-aware. Before retrying a write or other non-idempotent action, reconcile whether the prior attempt already took effect. If the state cannot be established, pause for safe recovery rather than replaying blindly.

## 7. Data resolution and storage

### 7.1 Data Resolver

The resolver selects a source based on freshness, authority, permissions and task requirements. Candidate sources are current execution context, CADSS/Memory, explicitly registered database services, registered APIs, local Vision/OCR and configured AI providers. This is a policy-driven choice, not a rigid priority list.

The resolver records source provenance, timestamp, freshness and permission decision with the result. It must not use stale cached data when the goal requires current state.

### 7.2 CADSS and Vision Memory

CADSS remains the canonical structured-data format and registry. SNM entities are stored as versioned CADSS collections and referenced by stable IDs.

Vision Memory retains the existing agent-owned model:
- original screenshots remain immutable evidence;
- crops and overlays are separate derived assets;
- feature records include pHash, OCR, bounding boxes and recognizer versions;
- each asset links to Screen, Element, Transition and Execution references where applicable;
- candidate observations are not treated as verified templates.

Use the existing app-private asset storage and backup/export mechanism. Do not create a second competing image repository.

### 7.3 Storage responsibilities

- CADSS/SQLite-backed store: structured entities, indexes, graph relationships, state and execution metadata.
- App-private file storage: original screenshots, crops and derived visual assets.
- CASL registry: versioned Workflow/Skill definitions and dependency pins.
- API Registry: registered action/tool contracts and permission metadata.

The current repository documentation notes that parts of CADSS still use local JSON/SharedPreferences and that some capabilities remain incomplete. Migration to indexed SQLite tables must be incremental, versioned and backed by import/export compatibility tests; do not claim it already exists.

### 7.4 Versioning and backup

Version Screen, state variant, layout profile, element schema, transition, path, action definition, CASL program and recognizer profile. Preserve historical records. Incompatible schema changes require migrations.

Backup includes CADSS metadata, SNM graph, CASL/Skill/Workflow definitions, dependency pins, relevant execution evidence and referenced Vision Memory assets. Restore validates integrity and cross-references before replacing live data. The existing user-controlled backup/restore policy remains unchanged.

## 8. Learning and governance

Learning is evidence-driven:

Observe → Candidate → Repeated observation → Controlled test → Verified → Available for planning.

AELM may analyze navigation outcomes, recognition failures and action metrics and propose improvements to scripts, Skills, Workflow or recognition profiles. It must not directly mutate active runtime state.

Keep the established generation governance:
- generation permissions are granular;
- approval is strict;
- prohibited generation is handled automatically by policy;
- generated artifacts begin as Draft/Candidate;
- validation, testing and approval precede release;
- prior versions remain available for rollback.

Do not learn from unverified or prohibited interactions. Do not store sensitive screen contents unless required, authorized and covered by the app's data policy. Apply retention and redaction rules to screenshots, OCR and execution traces.

## 9. Action security and failure handling

All actions pass through the registered Action Runtime. Enforce least-privilege permissions for vision read, screen read/interact, database read/write, API call, memory read/write, script execution, Skill/Workflow execution, generation and release.

Classify actions as read-only, reversible, persistent-write, high-impact or prohibited. Apply confirmation and approval policy accordingly. Pause/Resume/Stop remains available through the existing user controls.

Errors are categorized as observation, recognition, coordinate, navigation, action, API, database, script, state or memory failures. Recovery sequence:

1. Stop the affected branch.
2. Capture and classify current state.
3. Reconcile completed and pending actions.
4. Validate checkpoint and dependency versions.
5. Replan only the remaining work.
6. Revalidate permissions and script.
7. Resume only when the state is known and safe.

Watchdog monitors service health and data integrity; it does not blindly replay actions.

## 10. Implementation plan and acceptance criteria

Implement incrementally against existing modules. Do not create duplicate AI, CASL, Workflow, CADSS or Vision Memory systems.

| Phase | Scope | Acceptance |
|---|---|---|
| 1 | Coordinate metadata and visual evidence | Frame-aware coordinates; crop/overlay provenance; no source-image mutation |
| 2 | SNM schema and persistence | CRUD, indexes, versioning, backup/restore and relationship tests |
| 3 | Screen/state/element recognition | Distinguishes screen identity from state variants; confidence and evidence are retained |
| 4 | Transition graph and planner | Finds valid paths; rejects stale edges; records verified outcomes |
| 5 | Action Runtime contracts | Permission-checked dispatch, structured results, verification and cancellation |
| 6 | AII and CASL integration | Validated plans become version-pinned CASL; no direct AI-to-Android calls |
| 7 | Recovery and AELM | Checkpoint reconciliation, idempotency protection and governed learning |
| 8 | End-to-end validation | Unit, migration, regression, backup/restore and real-device tests |

Required tests include different resolutions/orientations, insets and crop offsets; a moved element; popup versus base screen; loading/offline variants; stale transition rejection; navigation failure and replanning; permission denial; duplicate non-idempotent action prevention; backup/restore of graph and assets; and CASL dependency-version mismatch.

## 11. Current implementation status

This document is an architecture specification, not a claim that the features are implemented.

The current main branch already contains foundational pieces documented elsewhere: HVCS grid/normalized-coordinate analysis, local OCR and pHash-based Vision Memory, Accessibility action execution, CADSS registries, a CASL interpreter, Workflow/Skill infrastructure and AELM lifecycle components.

The repository's existing architecture and roadmap documents explicitly identify remaining gaps such as end-to-end observe/decide/act/verify, durable execution recovery, real API adapters, deterministic UI-state verification, complete navigation learning and real-device validation.

AII, SNM graph persistence, verified transition planning, unified data resolution and their end-to-end integration must therefore be implemented and tested in the phases above. A successful documentation commit or CI build alone does not establish those capabilities as operational.
