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

```bash
npm run start --dev-client
```

- For native changes (Kotlin, manifest, layouts), rebuild and install on Android:

```bash
npx expo run:android
```

If you're consuming this module locally from the monorepo, keep the module in `modules/expo-persistent-bubble` and run the same commands from the app root.

**Runtime permissions**
- The module uses the Android "Draw over other apps" permission (`SYSTEM_ALERT_WINDOW`). `Bubble.start()` will open the system settings page to let users grant that permission when needed.

**API summary (JS)**

- `start(): Promise<void>`: Starts the overlay service. If overlay permission is missing, opens the overlay settings.
- `stop(): void`: Stops the overlay and removes the bubble.
- `config(options: object): void`: Batch-set options such as `iconSizeDp`, `setIcon`, `trashIcon`, `trashIconSizeDp`, `trashHidden`.
- `setIcon(source | { source, sizeDp }): void`: Update the bubble icon. Accepts `data:` URIs, `file://`, `content://`, or filesystem paths.
- `setIconSize(dp: number): void`: Set icon size in dp.
- `setTrashIcon(...)`, `setTrashIconSize(...)`, `setTrashHidden(...)`: Trash-related setters.
- `setAppStateAutoHide(enabled: boolean)`: Enable JS-managed auto-hide when the app is active.
- `hasOverlayPermission(): Promise<boolean>`: Query overlay permission.
- `isActive(): Promise<boolean>`: Check whether the overlay service is running.
- `onIconRemoved(handler)`: Subscribe to the `iconRemoved` event; returns `{ remove() }`.

See `src/PersistentBubbleModule.js` for the full API and small React-style hooks exposed by the module (e.g., `autoHideState()` and `isActiveState()`).

**Icon sources**

- `data:` base64 image URIs (e.g., `data:image/png;base64,...`).
- `file://` paths or `content://` URIs obtained from image pickers.
- Absolute filesystem paths.

**Behavior notes**

- Drag to the bottom to reveal a trash area — dropping the bubble there removes it.
- After dragging, the bubble snaps to the nearest edge. Position and edge are persisted using `SharedPreferences`.
- On rotation the module preserves the edge (left/right) and approximates vertical position via a saved ratio.

**Development tips & troubleshooting**

- If the bubble does not appear, verify overlay permission in Android settings.
- If icons don't load, confirm the URI is accessible from the app process (content URIs sometimes need special handling).
- For native changes, always run `npx expo run:android` after modifying Kotlin/Android resources.

**Example: set an icon from an image picker**

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

**Where to change behavior**

- JS helpers and API: `src/PersistentBubbleModule.js` and `Demo.js` (for playground).
- Native behavior (snap animation, trash area, persistence): `android/src/main/java/.../FloatingIconService.kt` and related classes.
