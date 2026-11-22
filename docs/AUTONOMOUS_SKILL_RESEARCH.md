# Autonomous Skill Research

## Purpose

Skill research is a live, screenshot-driven Agent session. It is not a one-shot Gemini prompt that drafts a Skill from its description.

## Runtime loop

1. The user starts one Skill or the batch queue from the Skill Library.
2. The app checks the Gemini key supplied in the current in-memory session and Accessibility availability.
3. The Agent opens Clash of Clans and captures the current screen through the Android Accessibility screenshot API.
4. The Agent runs local OCR and pHash, saves the original screenshot and its standard crop, and sends the screenshot plus OCR and the current research database snapshot to Gemini.
5. Gemini returns a structured observation, a next research action, UI layout records, procedure updates, strategies, calculations, datasets, crop requests, and the evolving Skill manifest/CASL.
6. The app validates the response shape, persists structured data, crops requested regions, and executes only bounded in-game tap/swipe/wait/observe actions.
7. After a gesture, the next screenshot is sent to Gemini. Gemini must assess the previous action as success, failed, or uncertain. Evidence is written back to the action's observation record.
8. The loop continues until Gemini explicitly reports researchComplete and returns a matching manifest and CASL document, the user stops, the Agent needs user input, or a safety/runtime budget is reached.
9. The Skill is saved as RESEARCHED, never VERIFIED. Testing and verification remain separate lifecycle steps.

## Action boundaries

- Only the currently foreground Clash of Clans package may receive research gestures.
- Coordinates are normalized to the 0..1000 display range and checked before dispatch.
- Research gestures are limited to tap, swipe, wait, and observe. No shell, arbitrary code, external app navigation, purchases, paid offers, account linking, chat, or destructive confirmations.
- A session has cycle, elapsed-time, action-count, request-timeout, and per-gesture limits. A user can stop the session from the Skill Library.
- A failed or uncertain post-action observation cannot be reported as a successful test. The Agent must continue observing or stop safely.

## Research data model

The local SQLite database `skill_research.db` contains:

- `sessions`: goal, Skill ID, lifecycle, timestamps and cycle summary.
- `observations`: OCR, pHash, screen key, model analysis, screenshot reference, crop references, action and post-action verification.
- `layouts`: versioned screen layout snapshots.
- `layout_components`: normalized bounding boxes, labels, component type, anchors and properties.
- `base_layouts` and `base_layout_items`: separate Clash of Clans village, war and builder-base layouts, building placements, levels, rotations, footprints, constraints and evidence confidence.
- `procedure_steps`: ordered preconditions, action, postconditions and evidence links.
- `strategies`: strategy records with kind, structured content, confidence and evidence.
- `calculations`: descriptive formula, inputs, result and evidence.
- `custom_datasets`: AI-created named datasets with JSON schema and records. The Agent can page through every table with bounded database queries and create/update/delete its session-owned datasets.
- `media_assets`: indexed references to original and cropped media.
- `skill_documents`: final manifest and CASL document.

Images are stored as app-private media files. SQLite and CADSS keep stable file references and metadata, not image bytes. CADSS stores the media references used by the backup pipeline.

## Import and export

The existing CADSS ZIP backup now contains:
- `cadss.json` for CADSS collections and Skill/Workflow registry data;
- `research/research.json` for the complete versioned SQLite research database;
- `vision/*` for every original and crop asset referenced by Vision Memory and research media.

Restore validates the archive entries and media references before importing the database. Older CADSS archives without a research database remain supported.

## Batch research

The batch action snapshots all latest-version Skills in RESEARCH_REQUIRED and runs one autonomous session at a time. A failure in one Skill is recorded and does not prevent later Skills from being attempted.

## Lifecycle guarantee

Autonomous research creates a RESEARCHED version only. It does not grant VERIFIED status or execute the resulting CASL as a trusted Skill. A separate test/verification workflow is required before normal Agent execution.
