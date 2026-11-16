# CoC AI Assistant

A local-first Android companion with a premium dark dashboard, versioned personal data, user-initiated image analysis, and optional Gemini assistance.

## Included modules

- **CADSS** — versioned local document store for notes, collections, and metadata.
- **CASL** — declarative JSON rule evaluator with IF/ELSE branches and a restricted operator set. It does not execute arbitrary code.
- **Vision Studio** — user-selected images processed locally with native OpenCV; creates a 64-bit perceptual hash.
- **Vision Memory** — stores original and center-crop images in app-private storage with searchable metadata.
- **Template learning** — records novel visual hashes and finds nearest stored templates by Hamming distance.
- **Gemini client** — optional user-triggered text analysis. The API key is held in the active screen state and is not written to local storage.
- **Scheduler** — deferrable daily maintenance through WorkManager.
- **Backup / Restore** — ZIP archive containing the versioned CADSS JSON document and Vision Memory assets.

## UI

The Compose interface contains five destinations: Overview, Vision, CASL, Scheduler, and Settings. The design uses a dark navy palette, violet primary actions, cyan status accents, metric cards, a persistent bottom navigation bar, and an in-app floating quick dashboard. The session timer supports 30- and 60-minute countdowns.

## Build

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21 with the Kotlin Compose compiler plugin
- JDK 17, Gradle 8.9
- Android API 23+ (compile / target API 35)

Run the **Android APK** GitHub Actions workflow. Successful runs publish the `CoC-AI-Assistant-debug` artifact.

## Privacy and scope

- Images are processed only after the user selects them.
- Original and center-crop images are stored in app-private storage.
- Gemini requests are sent only after the user enters an API key and presses the request button.
- The API key is not persisted by the application.
- The app does not request Accessibility, overlay, or screen-capture permissions and does not automate or interact with the Clash of Clans client.

## Current limitations

- The maintenance worker is a lightweight scheduled hook; detailed cleanup policies and user-configurable schedules remain future work.
- The floating dashboard is an in-app quick panel, not a system-wide overlay.
- Backup restore validates the archive and schema but does not yet provide a merge/replace choice.
- The app requires an on-device install and manual smoke test before release. A successful CI build alone is not proof of runtime correctness.
