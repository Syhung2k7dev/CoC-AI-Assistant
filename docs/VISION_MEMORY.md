# Vision Memory and Controlled Auto-learning

## Local processing
- OpenCV computes a 64-bit perceptual hash locally.
- VisionMemory stores original and center-crop JPEG assets in app-private storage.
- CADSS stores only metadata, hashes and relative asset names; image files are resolved through a canonical-path check.
- Images are decoded with bounded dimensions and pixel count before analysis.

## Learning lifecycle
1. An observation is compared against VERIFIED templates and PENDING_REVIEW candidates.
2. A novel observation is stored as a candidate with local image assets and pHash.
3. Similar later observations increase the candidate sighting count instead of creating duplicates.
4. A person must explicitly approve a candidate and confirm its label before it becomes a VERIFIED template.
5. Rejected candidates have their image assets deleted.
6. Runtime outcomes update a smoothed confidence estimate. Repeated failures can move a template to REVIEW_REQUIRED; such templates are excluded from matching.

## CASL integration
The Workflow API `vision.match` accepts a 16-character hexadecimal pHash and returns a local match result, template ID, label and Hamming distance. It reads only VERIFIED templates and does not upload image data.

## Backup
Vision candidates, templates and AELM test receipts are CADSS collections and are included in schema v2 backup/export. Original image assets remain in app-private storage and are copied by the app backup workflow when enabled.
