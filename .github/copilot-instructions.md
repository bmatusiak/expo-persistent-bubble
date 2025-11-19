# Copilot Instructions: expo-persistent-bubble

These notes make AI agents productive in this repo immediately. Focus on Android-only Expo module development and the JS <-> native bridge.

## Scope & Architecture
- Android-only Expo module that renders a draggable overlay “bubble” via an Android `Service` using `TYPE_APPLICATION_OVERLAY`. Permission gate: `SYSTEM_ALERT_WINDOW`.
- JS entry `index.js` re-exports the module from `src/PersistentBubbleModule.js`, which uses `requireNativeModule('PersistentBubble')`.
- Android source located in `android/src`.
- Kotlin module `PersistentBubbleModule` starts/stops `FloatingIconService` and pushes UI config (icon source/size, trash icon/size, hide flag) via an Intent payload (JSON). The service draws the views and handles drag, snap-to-edge, and “trash” removal.
- Native -> JS events: `overlayActiveChanged`, `iconRemoved` (exposed by the module, forwarded to JS listeners).

## JS API Surface (exported from `src/PersistentBubbleModule.js`)
- `start()`: checks permission; if granted, starts overlay and applies saved icon size; otherwise opens overlay settings.
- `stop()`: stops overlay. If auto-hide is enabled, this disables auto-hide instead of stopping immediately.
- `config({ iconSizeDp?, setIcon?, trashIconSizeDp?, trashIcon?, trashHidden? })`: batched config helper.
- `setIcon(source | { source, sizeDp })`, `setIconSize(dp)`; trash variants: `setTrashIcon`, `setTrashIconSize`, `setTrashHidden`.
- `setAppStateAutoHide(enabled)`, `getAppStateAutoHide()`: JS-managed auto-hide using `AppState` and an internal `NodeEventEmitter`.
- `hasOverlayPermission()`, `isActive()`.
- React-style state hooks: `autoHideState()` and `isActiveState()` return state values that update via native/JS events.
- Listener: `onIconRemoved(handler)` returns `{ remove() }`.
- iOS is a no-op; every call guards with `Platform.OS === 'android'`.

## Native Module Details
- Kotlin: `android/src/main/java/expo/modules/persistentbubble/*`
  - `PersistentBubbleModule.kt`: defines `AsyncFunction`s/`Function`s, declares `Events(...)`, and maintains last config values to serialize into a JSON payload (`buildConfigJson`). Static `state` string is used for a simple JS-accessible store (`getState`/`setState`).
  - `FloatingIconService.kt`: adds/removes overlay views, drag logic, snap-to-edge animation, shows a bottom trash zone; persists position in `SharedPreferences` as `lastX`, `lastY`, and rotation-aware `lastEdge`, `lastYRatio`.
  - `ActiveChangeNotifier`, `IconRemovedNotifier`: simple notifiers bridged to JS events.
- Manifest: declares the `Service` and overlay permissions in `android/src/main/AndroidManifest.xml`.
- Layouts and drawables: `android/src/main/res/layout/*.xml`, `res/drawable/*` define icon and trash UIs.

## Build & Run (host app)
- This is a library module. Build it by running a native Android app that depends on it.
- Typical workflow in the host app:
  ```bash
  cd /path/to/your/expo-app
  npx expo run:android
  ```
- Assume expo dev server is running for JS bundling.
- Do `npx expo run:android` again after making native changes and read output logs for build success/failure.

Agent-run command
- If you'd like me to run the native build/install command for you, ask me and I can execute `npx expo run:android` in this workspace. Note: running this requires a configured Android SDK/emulator or connected device and may take several minutes.
- After modifing JS-only code, update `Demo.js` if needed to use new code for testing new changes.
- After modifing JS-only code, update `README.md` if needed to show how to use new code.
- Local cleanup: `npm run clean` removes `android/build`.
- Gradle SDKs are pinned in `android/build.gradle` (`compileSdk/targetSdk 36`, `minSdk 24`). Set `useManagedAndroidSdkVersions=true` to delegate versions to `expo-modules-core` if desired.

## Conventions & Patterns
- Always guard Android-only behavior (`Platform.OS !== 'android'` -> early return or safe default).
- Icon sources accept: base64 data URI (`data:image/...`), `file://` or `content://` URIs, or absolute file paths.
- JS ↔ Native config is passed as a compact JSON string via an Intent with action `CONFIG`. When the service is active, setters immediately re-send config.
- Persisted view position keys: `lastX`, `lastY`, and rotation-aware `lastEdge`/`lastYRatio` (keep these stable when modifying persistence).
- Avoid adding native foreground notifications; the service is meant to be unobtrusive and sticky (`START_STICKY`).

## Extending the Module
- Adding a new config option:
  - JS: add a setter and/or include in `config(...)`. Forward to native via `NativePersistentBubble.*`.
  - Kotlin: track a `last*` field in `PersistentBubbleModule.kt`, include it in `buildConfigJson()`, and handle it in `FloatingIconService.onStartCommand` under `action == "CONFIG"`.
- Adding a new event:
  - Kotlin: add to `Events(...)`, call `sendEvent(...)` (e.g., from notifiers). Expose a notifier similar to `ActiveChangeNotifier`/`IconRemovedNotifier` if needed.
  - JS: subscribe via `NativePersistentBubble.addListener('eventName', handler)` and wrap with a friendly API that returns `{ remove() }`.
- iOS behavior: keep methods as safe no-ops. Do not throw on iOS.

## Key Files
- JS: `index.js`, `src/PersistentBubbleModule.js`, `Demo.js` (usage example with base64 icons, auto-hide, and permission UI).
- Native: `android/src/main/...` Kotlin sources, `AndroidManifest.xml`, `res/layout/*`, `res/drawable/*`.
- Expo config: `expo-module.config.json` (`modules: ["expo.modules.persistentbubble.PersistentBubbleModule"]`).

---

**Testing & Demo Code**

- For all quick tests, UI demos, or JS API experiments, edit the `Demo.js` file in this module.
- `Demo.js` is the canonical place for validating changes to the JS API or native bridge. Add new buttons, UI, or test logic here for rapid iteration.
- Do not add test/demo code to `PersistentBubbleModule.js` or native sources; keep all such code in `Demo.js`.

If anything here is unclear or you need deeper guidance (e.g., publishing, CI, or adding iOS parity), ask and I’ll refine these instructions.
