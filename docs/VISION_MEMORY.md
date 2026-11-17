# Vision Memory – Agent-owned perception and auto-learning

## Local processing
- OpenCV computes a 64-bit perceptual hash locally.
- VisionMemory stores original and center-crop JPEG assets in app-private storage.
- CADSS stores only metadata, hashes and relative asset names; image files are resolved through a canonical-path check.
- Images are decoded with bounded dimensions and pixel count before analysis.

## Learning lifecycle
1. Only the Agent submits screen observations; users do not upload images or manage templates from the UI.
2. A novel observation is stored as a candidate with local image assets and pHash.
3. Similar later observations increase the candidate sighting count instead of creating duplicates.
4. The Agent may promote a candidate after at least three consistent local observations; promotion is recorded as agent_auto_learning. Low-confidence templates can still be moved to REVIEW_REQUIRED by the watchdog feedback path.
5. The Agent manages candidate/template lifecycle internally; no user-facing image-review workflow is exposed.
6. Runtime outcomes update a smoothed confidence estimate. Repeated failures can move a template to REVIEW_REQUIRED; such templates are excluded from matching.

## CASL integration
The Agent computes pHash locally from its own screen captures and supplies the local match/candidate result to its reasoning context. The Workflow API `vision.match` accepts a 16-character hexadecimal pHash and returns a local match result, template ID, label and Hamming distance. It reads only VERIFIED templates and does not upload image data. The Skill-scoped `vision.feedback` API records a success/failure outcome for a matched template, updates its smoothed confidence and moves repeatedly failing templates to REVIEW_REQUIRED. Direct Workflow calls cannot submit feedback outside a version-pinned Skill.

## Backup
Vision candidates, templates and AELM test receipts are CADSS collections and are included in schema v2 backup/export. Original image assets remain in app-private storage and are copied by the app backup workflow when enabled.
