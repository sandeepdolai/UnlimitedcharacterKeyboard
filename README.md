# Pasteboard Keyboard

An Android keyboard with an iPhone-inspired visual direction and a local-first text library. The app does not impose a character limit on saved snippets.

## Features in this version

- Redesigned rounded-key QWERTY layout with key previews and alternate-character long presses.
- Three-slot offline word suggestions based on the current word prefix.
- Shift, caps lock, number/symbol layouts, contextual Enter key and double-space period.
- Cursor positioning by long-pressing the Space key and moving left or right.
- Emoji picker with categories.
- Local clipboard/snippet library with search, paste and favourites.
- One-handed layout, light/dark keyboard appearance and optional haptic feedback.
- Password fields hide clipboard content.
- Snippets stay on the device; no network permission or account is required.

The current suggestion engine is a small offline prefix matcher. It is not equivalent to Apple's predictive language model or full autocorrection. Swipe-to-type, voice dictation, GIF search, translation and AI writing tools are not included in this first redesigned build.

## Build an APK with GitHub Actions

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Choose **Run workflow** on the `main` branch, or wait for a build after a push.
4. Open the completed run and download **PasteboardKeyboard-debug-apk** under Artifacts.
5. Extract the downloaded ZIP to find `app-debug.apk`.

## Build locally

Open the `PasteboardKeyboard` folder in Android Studio, let Gradle sync, then select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The workflow creates a debug APK for testing, not a Play Store release. Real storage limits and the destination app's text-field limits still apply.
