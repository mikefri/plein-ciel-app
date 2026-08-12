import React, { useEffect, useRef } from 'react';
import { BackHandler, Platform, StatusBar, View } from 'react-native';
import { WebView } from 'react-native-webview';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Notifications from 'expo-notifications';
import * as TaskManager from 'expo-task-manager';
import * as BackgroundFetch from 'expo-background-fetch';

const SITE_URL = 'https://mikefri.github.io/Plein_Ciel/';
const TASK_NAME = 'pleinciel-background-check';
const CHANNEL_ID = 'pleinciel-alerts';

// Configure les notifications
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

// Tâche de fond : vérifie la météo toutes les 15 min
TaskManager.defineTask(TASK_NAME, async () => {
  try {
    const raw = await AsyncStorage.getItem('pc_widget_loc');
    if (!raw) return BackgroundFetch.BackgroundFetchResult.NoData;
    
    const loc = JSON.parse(raw);
    const lat = loc.lat || 48.85;
    const lon = loc.lon || 2.35;
    const city = loc.name || 'Plein Ciel';
    
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&hourly=weather_code&timezone=auto&forecast_days=1`;
    const response = await fetch(url);
    const data = await response.json();
    
    const now = new Date();
    const hourIndex = now.getHours();
    const codes = data.hourly?.weather_code || [];
    
    for (let i = hourIndex; i < Math.min(hourIndex + 3, 24); i++) {
      const code = codes[i];
      if (code >= 95 && code <= 99) {
        await Notifications.scheduleNotificationAsync({
          content: {
            title: '⛈️ Alerte orage',
            body: `Orage prévu dans les 3h à ${city}`,
            data: { type: 'thunder' },
            sound: 'default',
          },
          trigger: null,
        });
        break;
      }
    }
    
    return BackgroundFetch.BackgroundFetchResult.NewData;
  } catch (e) {
    console.error('Background task error:', e);
    return BackgroundFetch.BackgroundFetchResult.Failed;
  }
});

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

async function setupNotifications() {
  // 1. Crée le canal de notification (Android 8+)
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: 'Alertes orage',
      importance: Notifications.AndroidImportance.HIGH,
      sound: 'default',
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#1470c8',
    });
  }
  
  // 2. Demande la permission (méthode officielle expo-notifications)
  const { status: existing } = await Notifications.getPermissionsAsync();
  if (existing !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync({
      android: { allowAlert: true, allowBadge: true, allowSound: true },
    });
    console.log('Permission notifications:', status);
  }
}

export default function App() {
  const ref = useRef(null);

  useEffect(() => {
    setupNotifications();
    
    BackgroundFetch.registerTaskAsync(TASK_NAME, {
      minimumInterval: 15 * 60,
      stopOnTerminate: false,
      startOnBoot: true,
    });
    
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (ref.current) { ref.current.goBack(); return true; }
      return false;
    });
    return () => sub.remove();
  }, []);

  const onMessage = async (event) => {
    try {
      const data = event.nativeEvent.data;
      
      // COMMANDE DE TEST : si le site envoie "test_notification", on envoie une notif
      if (data === 'test_notification') {
        await Notifications.scheduleNotificationAsync({
          content: {
            title: '🔔 Test Plein Ciel',
            body: 'Les notifications fonctionnent ! ✅',
            sound: 'default',
          },
          trigger: null,
        });
        return;
      }
      
      await AsyncStorage.setItem('pc_widget_loc', data);
      
      if (data.startsWith('alert:')) {
        const alertData = JSON.parse(data.substring(6));
        await Notifications.scheduleNotificationAsync({
          content: {
            title: alertData.title || '⛈️ Alerte orage',
            body: alertData.body || 'Orage imminent',
            data: { type: 'thunder' },
            sound: 'default',
          },
          trigger: null,
        });
      }
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
