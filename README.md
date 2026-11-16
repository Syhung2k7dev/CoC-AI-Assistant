# CoC-AI-Assistant

A lightweight Android companion app for personal notes and manual session timers.

## Current MVP

- Local notes saved on the device.
- Manual 30-minute and 1-hour countdown presets.
- Stop/reset control.
- GitHub Actions workflow that builds a debug APK on pushes to `main`.

## Build artifact

After a successful workflow run, open **Actions → Android APK → Artifacts** and download **CoC-Companion-debug**.

## Compatibility

- Android 6.0 (API 23) and newer.
- Built with JDK 17, Gradle 8.9, and Android Gradle Plugin 8.7.3.

## Privacy and scope

This MVP does not request Accessibility, screen-capture, overlay, or account credentials permissions. It does not automate gameplay, interact with the Clash of Clans client, switch game accounts, or attempt to evade game integrity or anti-cheat systems. Notes remain in app-private local storage.

## Roadmap

Future additions can focus on user-controlled planning, reminders, manual progress tracking, and transparent AI-assisted explanations without autonomous game control.
