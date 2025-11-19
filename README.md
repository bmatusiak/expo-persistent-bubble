# expo-persistent-bubble

Android-only Expo module providing a persistent, draggable floating bubble (chat-head style) that sits on top of other apps. The bubble is implemented as an Android `Service` and supports customization, persistence of position, snap-to-edge behavior, and a trash area for removal.

This README covers usage, API surface, and quick dev instructions. The module is built with the Expo Modules API and is intended to be used from an Android app (local module usage from `modules/expo-persistent-bubble` is supported during development).

**Where to look**:
- **JS API**: `src/PersistentBubbleModule.js`
- **Demo & quick tests**: `Demo.js` (in the module root)
- **Native Android**: `android/src/main/java/...` and `android/src/main/AndroidManifest.xml`

**Important**: Guard calls with `Platform.OS === 'android'` if your app also targets iOS — the JS API early-returns on non-Android.

**Quick highlights**
- **Tap**: returns the user to your app.
- **Drag**: snap-to-edge and a bottom trash target to dismiss.
- **Persistence**: remembers last edge/position across restarts.
- **Configurable**: icon source, icon size, trash icon/visibility, auto-hide behavior.

**Getting started (dev)**

- Start the Expo dev server (JS-only changes):

#+ expo-persistent-bubble

Android-only Expo module that provides a persistent, draggable floating bubble (chat-head style) which sits above other apps. The bubble runs inside an Android `Service`, supports icon customization, snap-to-edge behavior, persistence of position, and a trash drop target for removal.

**Where to look**
- **JS API**: `src/PersistentBubbleModule.js`
- **Demo / playground**: `Demo.js` (module root)
- **Native Android**: `android/src/main/java/...` and `android/src/main/AndroidManifest.xml`

**Quick notes**
- **Android-only**: All API calls guard for `Platform.OS === 'android'` and are no-ops on other platforms.
- **Permissions**: Uses the `SYSTEM_ALERT_WINDOW` overlay permission (the module opens settings when needed).
- **Icon sources**: Accepts `data:` (base64), `file://`, `content://`, and file paths.

**Getting started (development)**

- **Start JS (dev client)**:

```bash
npm run start --dev-client
```

- **Rebuild for native changes (Kotlin / Android resources)**:

```bash
npx expo run:android
```

If you are working in the monorepo, keep this module at `modules/expo-persistent-bubble` and run the commands from the app root.

**API (summary)**

- `start(): Promise<void>`: Starts the overlay service. If overlay permission is missing, opens system settings to let the user grant it.
- `stop(): void`: Stops the overlay and removes the bubble.
- `config(options: object): void`: Apply several config options at once (e.g., `iconSizeDp`, `setIcon`, `trashIcon`, `trashIconSizeDp`, `trashHidden`).
- `setIcon(source | { source, sizeDp }): void`: Update the bubble icon. `source` may be a `data:` URI, `file://`, `content://`, or local path.
- `setIconSize(dp: number): void`
- `setTrashIcon(...)`, `setTrashIconSize(...)`, `setTrashHidden(...)`
- `setAppStateAutoHide(enabled: boolean)`: Enable JS-managed auto-hide when the app is in the foreground.
- `hasOverlayPermission(): Promise<boolean>`
- `isActive(): Promise<boolean>`
- `onIconRemoved(handler)`: Subscribe to `iconRemoved` events; returns `{ remove() }`.

See `src/PersistentBubbleModule.js` for the full API and small React-style hooks (e.g., `autoHideState()` and `isActiveState()`).

**Icon source examples**

- Base64 `data:` URI: `data:image/png;base64,...`
- `file://` or `content://` URIs from image pickers
- Absolute filesystem paths

**Behavior & persistence**

- Dragging the bubble reveals a bottom trash area — dropping onto it removes the bubble.
- After drag, the bubble snaps to the nearest screen edge. Position is persisted in `SharedPreferences` (`lastX`, `lastY`, `lastEdge`, `lastYRatio`).
- On rotation we preserve the edge (left/right) and approximate vertical position via a saved ratio.

**Example: pick an image and set icon**

```ts
import * as ImagePicker from 'expo-image-picker';
import Bubble from 'expo-persistent-bubble';

const pickAndSetIcon = async () => {
  const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images });
  if (!result.canceled) {
    const asset = result.assets[0];
    Bubble.setIcon(asset.uri);
  }
};
```

**Native notes (overview for contributors)**

- Kotlin service: `FloatingIconService.kt` manages overlay views, drag logic, snap animation, and trash behavior.
- Bridge: `PersistentBubbleModule.kt` builds a compact JSON payload (config) and sends it to the service via Intent action `CONFIG` when active.
- Events: `overlayActiveChanged` and `iconRemoved` are emitted to JS; see the native module's `Events(...)` declaration.

**Troubleshooting**

- If no bubble appears, confirm overlay permission in Android Settings.
- If icons fail to load, ensure the URI is accessible to the app process (some content URIs require grant flags).
- After Kotlin or resource changes, run `npx expo run:android` to rebuild.

**Contributing & testing**

- Use `Demo.js` for quick API testing in the app (this is the canonical playground).
- Keep Android-only native logic guarded behind `Platform.OS === 'android'`.

---

If you'd like, I can also update `Demo.js` with quick example buttons that exercise the new README examples. Tell me if you want that added.
