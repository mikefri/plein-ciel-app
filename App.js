import React, { useEffect, useRef } from 'react';
import { BackHandler, PermissionsAndroid, Platform, StatusBar, View } from 'react-native';
import { WebView } from 'react-native-webview';

const SITE_URL = 'https://mikefri.github.io/Plein_Ciel/';

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
        style={{ flex: 1 }}
      />
    </View>
  );
}