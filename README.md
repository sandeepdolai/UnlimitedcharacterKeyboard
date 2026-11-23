# Unlimited Character Keyboard

An Android Studio project for **Pasteboard Keyboard**, a local-first keyboard with a persistent library of text snippets.

## Build the APK with GitHub Actions

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Choose **Run workflow** on the `main` branch.
4. When the run completes successfully, open the run and download the **PasteboardKeyboard-debug-apk** artifact.

A build also starts automatically whenever code is pushed to `main`.

## Android project

The complete Android Studio project is available in [PasteboardKeyboard-AndroidStudio.zip](./PasteboardKeyboard-AndroidStudio.zip). It includes the Kotlin source, manifest, resources, Gradle configuration, and setup instructions.

The workflow verifies the archive checksum, extracts the project, builds `app-debug.apk`, and uploads it as a downloadable workflow artifact.

## Notes

- The workflow produces a **debug APK** for testing; it is not a Play Store release build.
- The keyboard stores snippets locally. It does not record everything typed.
- "No app-level character limit" does not mean infinite storage. Device storage, database limits, memory, and the destination app's text-field limits still apply.
