# Clash of Clans package discovery and launch

The Android target SDK is 35, so package visibility is restricted on Android 11 and newer. The manifest now declares a package-specific query for the global Clash of Clans package, `com.supercell.clashofclans`.

The Automation panel exposes:
- A package inspection action that distinguishes missing, disabled, and non-launchable installations.
- A launch action that resolves the launcher activity through PackageManager and starts it with a new task.
- Explicit status messages for missing packages, disabled games, missing launch intents, and Android launch/security failures.

The game must be installed in the same Android user/profile as CoC-AI-Assistant. Work profiles, cloned-app spaces, and vendor dual-app containers may isolate package visibility and installation state.
