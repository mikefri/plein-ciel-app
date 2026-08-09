import React, { useEffect, useRef } from 'react';
import { BackHandler, PermissionsAndroid, Platform, StatusBar, View } from 'react-native';
import { WebView } from 'react-native-webview';
import AsyncStorage from '@react-native-async-storage/async-storage';

const SITE_URL = 'https://mikefri.github.io/Plein_Ciel/';

const INJECTED = `
(function () {
  var last = '';
  setInterval(function () {
    try {
      var raw = localStorage.getItem('pc_loc');
      if (raw && raw !== last) {
        last = raw;
        window.ReactNativeWebView.postMessage(raw);
      }
    } catch (e) {}
  }, 3000);
})();
true;
`;

async function requestLocationPermission() {
  if (Platform.OS !== 'android') return;
  try {
    await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
      PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
    ]);
  } catch (e) {}
}

export default function App() {
  const ref = useRef(null);

  useEffect(() => {
    requestLocationPermission();
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (ref.current) { ref.current.goBack(); return true; }
      return false;
    });
    return () => sub.remove();
  }, []);

  const onMessage = async (event) => {
    try {
      await AsyncStorage.setItem('pc_widget_loc', event.nativeEvent.data);
    } catch (e) {}
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#1470c8' }}>
      <StatusBar backgroundColor="#1470c8" barStyle="light-content" />
      <WebView
        ref={ref}
        source={{ uri: SITE_URL }}
        javaScriptEnabled={true}
        domStorageEnabled={true}
        geolocationEnabled={true}
        mixedContentMode="always"
        injectedJavaScript={INJECTED}
        onMessage={onMessage}
        style={{ flex: 1 }}
      />
    </View>
  );
}