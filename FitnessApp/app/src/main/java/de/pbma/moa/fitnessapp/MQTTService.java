package de.pbma.moa.fitnessapp;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.pbma.moa.fitnessapp.mqtt.MqttMessaging;

public class MQTTService extends Service {
    final static String TAG = MQTTService.class.getCanonicalName();

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_PRESS = "press";

    private final CopyOnWriteArrayList<PressListener> listeners = new CopyOnWriteArrayList<>();

    private String deviceName;
    private String pressTopic;
    private MqttMessaging mqttMessaging;
    private boolean connected = false;

    // Logging an UI übergeben
    private void remoteLog(String line) {
        Log.v(TAG, "remoteLog: " + line);
        for (PressListener listener : listeners) {
            listener.onLogMessage(line);
        }
    }

    private void doMqttStatus(boolean connected) {
        for (PressListener listener : listeners) {
            listener.onMQTTStatus(connected);
        }
    }

    private void doOnPress(String topic, String msg) {
        for (PressListener listener : listeners) {
            listener.onPress(topic, msg);
        }
    }

    public boolean registerPressListener(PressListener pressListener) {
        return listeners.addIfAbsent(pressListener);
    }

    public boolean deregisterPressListener(PressListener pressListener) {
        return listeners.remove(pressListener);
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");
        super.onCreate();
        deviceName = "LiveViewer";
        //pressTopic = "25moagd/+/progress"; // 🔄 wildcard für alle User
        pressTopic = "25moagd/lobby";
        pressTopic = "25moagd/game/#";

    }

    public class LocalBinder extends Binder {
        public MQTTService getMQTTService() {
            return MQTTService.this;
        }
    }

    private final IBinder localBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PRESS.equals(action)) {
            return localBinder;
        } else {
            Log.e(TAG, "onBind only defined for ACTION_PRESS");
            return null;
        }
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        disconnect();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (action == null) {
            Log.w(TAG, "action=null, nothing further to do");
            return START_STICKY;
        }

        switch (action) {
            case ACTION_START:
                Log.v(TAG, "starting MQTT");
                remoteLog("starting");
                connect();
                return START_STICKY;
            case ACTION_STOP:
                Log.v(TAG, "stopping MQTT");
                remoteLog("stopping");
                disconnect();
                return START_NOT_STICKY;
            default:
                Log.w(TAG, "unknown action=" + action);
                return START_NOT_STICKY;
        }
    }

    // Listener für Fehler
    private final MqttMessaging.FailureListener failureListener = new MqttMessaging.FailureListener() {
        @Override
        public void onConnectionError(Throwable throwable) {
            connected = false;
            remoteLog("ConnectionError: " + throwable.getMessage());
        }

        @Override
        public void onMessageError(Throwable throwable, String msg) {
            remoteLog("MessageError: " + throwable.getMessage());
        }

        @Override
        public void onSubscriptionError(Throwable throwable, String topic) {
            remoteLog("SubscriptionError: " + throwable.getMessage());
        }
    };

    // Listener für Verbindungsstatus
    private final MqttMessaging.ConnectionListener connectionListener = new MqttMessaging.ConnectionListener() {
//        @Override
//        public void onConnect() {
//            connected = true;
//            remoteLog("connected");
//            Log.v(TAG, " MQTT connected");
//            mqttMessaging.subscribe(pressTopic); // jetzt verbinden
//            doMqttStatus(true);
//        }
@Override
public void onConnect() {
    connected = true;
    remoteLog("connected");
    Log.v(TAG, " MQTT connected");

    // Zentrale Subscriptions
    mqttMessaging.subscribe("25moagd/lobby");
    mqttMessaging.subscribe("25moagd/game/#");

    doMqttStatus(true);
}

        @Override
        public void onDisconnect() {
            connected = false;
            remoteLog("disconnected");
            doMqttStatus(false);
        }
    };

    // Listener für Nachrichten
    private final MqttMessaging.MessageListener messageListener = new MqttMessaging.MessageListener() {
        @Override
        public void onMessage(String topic, String stringMsg) {
            Log.e(TAG, " Nachricht empfangen auf Topic: " + topic + " → " + stringMsg);
            Log.v(TAG, "mqttService receives: " + stringMsg);
            remoteLog("received raw: " + stringMsg);
            doOnPress(topic, stringMsg);
        }
    };

    private void connect() {
        Log.v(TAG, "connect");
        if (connected) {
            Log.v(TAG, "already connected, skipping connect");
            return;
        }

        if (mqttMessaging != null) {
            disconnect();
            Log.w(TAG, "reconnect");
        }

        deviceName = "LiveViewer";

        //Wildcard verwenden!
        pressTopic = "25moagd/+/progress";

        String connectionURL = "ssl://mqtt.inftech.hs-mannheim.de:8883";

        mqttMessaging = MqttMessaging.getInstance(this, failureListener, messageListener, connectionListener);
        Log.v(TAG, "connectionURL=" + connectionURL);

        MqttConnectOptions options = MqttMessaging.getMqttConnectOptions();
        options.setUserName("25moagd");
        options.setPassword("3ada222c".toCharArray());
        Log.v(TAG, "username=25moagd");

        mqttMessaging.connect(connectionURL, options);
    }


    private void disconnect() {
        Log.v(TAG, "disconnect()");
        connected = false;
        if (mqttMessaging != null) {
            mqttMessaging.unsubscribe(pressTopic);
            List<MqttMessaging.Pair<String, String>> pending = mqttMessaging.disconnect();
            if (!pending.isEmpty()) {
                Log.w(TAG, "pending messages: " + pending.size());
            }
        }
        mqttMessaging = null;
    }

    public void sendMessage(String topic, String message) {
        if (mqttMessaging != null && connected) {
            Log.d(TAG, " Sende MQTT-Nachricht an Topic: " + topic + " → " + message);
            mqttMessaging.send(topic, message);
        } else {
            Log.e(TAG, "MQTT nicht verbunden. Nachricht wird nicht gesendet.");
        }
    }

}
