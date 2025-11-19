import { useState, useEffect } from 'react';
import {
  Button,
  AppState,
  Platform,
  StyleSheet,
  Text,
  View,
  ScrollView,
} from 'react-native';

// Import the Expo ported module
import PersistentBubble from 'expo-persistent-bubble';

//-- START DEMO for react-native-persistent-bubble --/

const icon1_base64 = 'iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABM0lEQVR4nO2bwQ6CQAwFwfjVeva78bQeSIxu+9rZhDdnoa9DWTGs+/Y4ju3C3OgANBZAB6CxADoAjQXQAWgsgA5AYwF0AJp7d8Hj9fsz+7M+x6dWx4+hf5r+RrWMUgGZxs9UiSgRoGz8jFqEfBGsbL7i/FIB1c1X1JEJ6GpeXU8ioLt5Zd20AKp5Vf2UALr5QSbH5R+FwwJWufqDaB5PQOSg1a7+IJLLE0AHoJkWsOr4D2bzeQLoADQWQAegsQA6AI0F0AFopgV0vrSIMJvPE0AHoAkJWPU2iOTyBEQPXG0Konk8AZmDV5mCTI70BNASsvUltwAlQVFXtgZ0S1DVky6CXRKUdeTfAuV7esTn9x4h7xJr/s/QJfcJrowfhekANBZAB6CxADoAjQXQAWgsgA5A8wbJSkY7p4NajAAAAABJRU5ErkJggg==';
const icon2_base64 = 'iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABWElEQVR4nO2awXHCMBQF1wxNpInkQGWkECpLDjQRynAOGWeUDFiyMbzR+39v3P6ubMuMPLx+HEcCs1MPoOR8OF3CBjgfThcIegVM8gB75SDPphSfCHMFXJOHIAFuyUOAAHPyYB6gJg/GAVrkwTRAq/zb5/uL1TbYKg4/8mD0HrBk1cvfFrfAWnkwCHCPPHQe4F556DjAFvLQaYCt5KGzXWDNNlejmwBbrnrJMI7j17qRPOjyGbAlGUA9gJoMoB5ATQZQD6AmA6gHUJMB1AOoyQDqAdRkAPUAajKAegA1GUA9gJoMoB5ATQZQD6AmfIBhzcfSjzqlUbDoaOwRZ3NqmgM4rXpJ0zPAVR4aAjjLQyWAuzzMBIggDzcCRJGHf7uA4zZX4zdApFUv2UFceVjwjZCjPDS+CLnKQ0MAZ3moBHCXh5kAEeThyr/BKOITf66AaPJQBIgoD/AN1gmDpNIPCYAAAAAASUVORK5CYII=';

export function PersistentBubbleDemo() {
  const [hasPermission, setHasPermission] = useState(false);
  const [trashHidden, setTrashHidden] = useState(false);
  const autoHideState = PersistentBubble.autoHideState();
  const overlayActiveState = PersistentBubble.isActiveState();
  const overlayHiddenState = PersistentBubble.isHiddenState();

  useEffect(() => {
    let cancelled = false;
    if (Platform.OS === 'android') {
      PersistentBubble.hasOverlayPermission()
        .then((v) => {
          if (!cancelled) setHasPermission(v);
        })
        .catch(() => {
          if (!cancelled) setHasPermission(null);
        });
    }
    return () => {
      cancelled = true;
    };
  }, []);

  if (Platform.OS !== 'android') return null;

  var permissionStatus = hasPermission === null ? 'Unknown' : hasPermission ? 'Granted' : 'Not granted';

  return (
    <ScrollView
      contentContainerStyle={PersistentBubbleDemoStyles.controls}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={PersistentBubbleDemoStyles.controlsTitle}>Persistent Bubble</Text>

      <Text style={PersistentBubbleDemoStyles.test}>Overlay State: {overlayActiveState ? 'Active' : 'Inactive'}</Text>

      <View style={PersistentBubbleDemoStyles.seperator} />

      <Text style={PersistentBubbleDemoStyles.sectionLabel}>Overlay permission</Text>

      <Text style={PersistentBubbleDemoStyles.text}>
        Status: {permissionStatus}
      </Text>


      {permissionStatus === 'Not granted' ? (<>
        <Text style={PersistentBubbleDemoStyles.text}>
          Please grant overlay permission in system settings.
        </Text>
        <View style={PersistentBubbleDemoStyles.buttonsColumn}>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Check"
              onPress={async () => {
                try {
                  const v = await PersistentBubble.hasOverlayPermission();
                  setHasPermission(v);
                } catch {
                  setHasPermission(null);
                }
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button title="Open Settings" onPress={() => PersistentBubble.start()} />
          </View>
        </View>
      </>) : (<>
        <View style={PersistentBubbleDemoStyles.seperator} />

        <View style={PersistentBubbleDemoStyles.buttonsColumn}>
          <View style={PersistentBubbleDemoStyles.button}>
            {!overlayActiveState ? (
              <Button title="Start" onPress={() => PersistentBubble.start()} />
            ) : (
              <Button title="Stop" onPress={() => PersistentBubble.stop()} />
            )}
          </View>

          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title={`AutoMode: ${autoHideState ? 'On' : 'Off'}`}
              onPress={() => {
                PersistentBubble.setAppStateAutoHide(!autoHideState);
              }}
            />
          </View>
        </View>

        <View style={PersistentBubbleDemoStyles.seperator} />

        <Text style={PersistentBubbleDemoStyles.sectionLabel}>Icon Config</Text>

        <View style={PersistentBubbleDemoStyles.buttonsColumn}>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Set Dummy Icon 1"
              onPress={() => {
                PersistentBubble.config({ setIcon: `data:image/png;base64,${icon1_base64}`, iconSizeDp: 64 });
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Reset Icon"
              onPress={() => {
                // pass false to reset to default
                PersistentBubble.setIcon(false);
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Set Dummy Icon 2 (Larger)"
              onPress={() => {
                PersistentBubble.config({ setIcon: `data:image/png;base64,${icon2_base64}`, iconSizeDp: 128 });
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Reset Icon (config)"
              onPress={() => {
                PersistentBubble.config({ setIcon: false });
              }}
            />
          </View>
        </View>

        <View style={PersistentBubbleDemoStyles.seperator} />

        <Text style={PersistentBubbleDemoStyles.sectionLabel}>Trash Zone Config</Text>

        <View style={PersistentBubbleDemoStyles.buttonsColumn}>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Trash: Small"
              onPress={() => {
                // Reuse icon1 as a dummy trash icon; normally supply a distinct image
                PersistentBubble.config({ trashIcon: `data:image/png;base64,${icon1_base64}`, trashIconSizeDp: 64 });
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Trash: Large"
              onPress={() => {
                PersistentBubble.config({ trashIcon: `data:image/png;base64,${icon2_base64}`, trashIconSizeDp: 128 });
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Reset Trash"
              onPress={() => {
                // reset trash via direct setter
                PersistentBubble.setTrashIcon(false);
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title={trashHidden ? 'Show Trash Zone' : 'Hide Trash Zone'}
              onPress={() => {
                const next = !trashHidden;
                setTrashHidden(next);
                PersistentBubble.setTrashHidden(next); // direct setter; could also batch via config({ trashHidden: next })
              }}
            />
          </View>
        </View>

        <View style={PersistentBubbleDemoStyles.seperator} />

        <Text style={PersistentBubbleDemoStyles.sectionLabel}>Overlay Visibility</Text>

        <Text style={PersistentBubbleDemoStyles.text}>
          Status: {overlayHiddenState ? 'Hidden' : 'Visible'}
        </Text>

        <View style={PersistentBubbleDemoStyles.buttonsColumn}>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Hide Overlay"
              onPress={async () => {
                try { await PersistentBubble.hide(); } catch (_) { }
              }}
            />
          </View>
          <View style={PersistentBubbleDemoStyles.button}>
            <Button
              title="Show Overlay"
              onPress={async () => {
                try { await PersistentBubble.show(); } catch (_) { }
              }}
            />
          </View>
        </View>
      </>)}

    </ScrollView>
  );
}

const PersistentBubbleDemoStyles = StyleSheet.create({
  controls: {
    padding: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#ccc',
    gap: 12,
  },
  controlsTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: '500',
    marginTop: 8,
    color: '#333',
  },
  button: {
    marginTop: 4,
    marginBottom: 4,
  },
  buttonsColumn: {
    // flexDirection: 'row',
    // alignItems: 'center',
    // flexWrap: 'wrap',
  },
  text: {
    fontSize: 12,
    color: '#666',
  },
  spacer: {
    width: 12,
  },
  seperator: {
    height: 1,
    backgroundColor: 'lightgray',
    marginVertical: 10, // Adds spacing above and below the line
  },
});


//-- END DEMO for react-native-persistent-bubble --/

// ** add more demos if needed ** //

export default { PersistentBubbleDemo };