package de.pbma.moa.fitnessapp.mqtt;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import de.pbma.moa.fitnessapp.PressListener;


// Simple MQTT Messaging class using the synchronous client in a separate thread.
// The user API is asynchronous handling all callbacks in yet another separate thread.
// ToDo: reconnect and resubscribe
public class MqttMessaging {

    //FreeWorkout
    private static MqttMessaging instance;
    private final List<PressListener> pressListeners = new ArrayList<>();

    final static String TAG = MqttMessage.class.getCanonicalName();
    private final String clientId;
    private volatile MqttClient client;
    private final AtomicBoolean connectPending = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false); // client connected and read to send

    private Context context;

    private final ConcurrentSkipListSet<String> subscriptions = new ConcurrentSkipListSet<>();
    private final ConcurrentHashMap<Long, Pair<String,String>> pendingMessages = new ConcurrentHashMap<>();
    private final AtomicLong pendingMessageId = new AtomicLong(0);

    private ExecutorService mqttExecutor;
    private ExecutorService callbackExecutor;

    public static class Pair<S, T> {
        private final S s;
        private final T t;

        public static <S, T> Pair<S, T> createPair(S s, T t) {
            return new Pair<>(s, t);
        }

        public Pair(S s, T t) {
            this.s = s;
            this.t = t;
        }

        public S getFirst() {
            return s;
        }

        public T getSecond() {
            return t;
        }
    }

    public interface MessageListener {
        void onMessage(String topic, String msg);
    }

    public interface ConnectionListener {
        void onConnect();
        void onDisconnect();
    }

    public interface FailureListener {
        void onConnectionError(Throwable throwable);
        void onMessageError(Throwable throwable, String msg);
        void onSubscriptionError(Throwable throwable, String topic);
    }

    private volatile MessageListener messageListener;
    private volatile ConnectionListener connectionListener;
    private volatile FailureListener failureListener;

    public MqttMessaging() {
        this(null, null, null);
    }

    public MqttMessaging(FailureListener failureListener) {
        this(failureListener, null, null);
    }

    public MqttMessaging(FailureListener failureListener, MessageListener messageListener) {
        this(failureListener, messageListener, null);
    }

    public MqttMessaging(Context context,
                         FailureListener failureListener,
                         MessageListener messageListener,
                         ConnectionListener connectionListener) {
        this(failureListener, messageListener, connectionListener); // ruft alten Konstruktor auf
        this.context = context.getApplicationContext(); // speichert Context für AndroidClient
    }


    private final MqttCallback mqttCallback = new MqttCallback() {

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            doMessage(topic, message);
        }
        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // ignore
        }
        @Override
        public void connectionLost(Throwable cause) {
            ready.set(false);
            Log.e(TAG, "connection lost: " + cause.getMessage());
            doConnectionFailure(cause); // connection lost is a connection failure
        }

    };

    public interface PressListener {
        void onPress(String topic, String msg);
    }
    public void registerPressListener(PressListener listener) {
        if (!pressListeners.contains(listener)) {
            pressListeners.add(listener);
        }
    }

    public void unregisterPressListener(PressListener listener) {
        pressListeners.remove(listener);
    }
    public MqttMessaging(FailureListener failureListener,
                         MessageListener messageListener,
                         ConnectionListener connectionListener) {
        this.failureListener = failureListener;
        this.messageListener = messageListener;
        this.connectionListener = connectionListener;
        clientId = MqttClient.generateClientId();
        Log.v(TAG, "constructed");
    }

    public MqttMessaging(Context context) {
        this(context, null, null, null);
    }

    public void connect(String broker) {
        connect(broker, null);
    }

    public static MqttConnectOptions getMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        return options;
    }

    public void connect(String broker, final MqttConnectOptions options) { // do in startCommand
        if (ready.get() || connectPending.get()) { // already connected and ready or soon ready
            return;
        }
        if (mqttExecutor != null) { // old executor for mqtt handling
            mqttExecutor.shutdown(); // cancel if exists
        }
        mqttExecutor = Executors.newSingleThreadExecutor();
        if (callbackExecutor != null) { // old executor for callbacks
            callbackExecutor.shutdown(); // cancel if exists
        }
        callbackExecutor = Executors.newSingleThreadExecutor();
        connectPending.set(true); // we are about to connect, ignore yet another connection request
        mqttExecutor.execute(() -> {
            try {
                // MemoryPersistence is useless if vm may restart
                client = new MqttClient(broker, clientId, null);
                client.setCallback(mqttCallback);
                MqttConnectOptions connectOptions = options;
                if (connectOptions == null) { // default
                    Log.w(TAG, "mqttExecutor: no connectOptions, defaulting");
                    connectOptions = MqttMessaging.getMqttConnectOptions();
                } else {
                    String passwd = new String(connectOptions.getPassword());
                    Log.v(TAG, String.format("mqttExecutor: username=%s, password=%s, ",
                            connectOptions.getUserName(), passwd));
                }
                client.connect(connectOptions); // blocking
                connectPending.set(false);
                ready.set(true);
                if (connectionListener != null) {
                    connectionListener.onConnect();
                }
                Log.v(TAG, "connected");
            } catch (MqttException e) {
                connectPending.set(false);
                ready.set(false);
                Log.e(TAG, "connection failure", e);
                String trace = Util.stacktrace(e);
                Log.e(TAG, trace);
                callbackExecutor.execute(() -> doConnectionFailure(e));
            }
        });
    }

    private void doConnectionFailure(Throwable e) {
        FailureListener f = failureListener;
        if (f != null) {
            f.onConnectionError(e);
        }
    }

    private void doMessageFailure(Throwable e, String topic, String msg) {
        FailureListener f = failureListener;
        if (f != null) {
            f.onMessageError(e, Util.fmt("%s: %d", topic, msg));
        }
    }

    private void doSubscriptionFailure(Throwable e, String topicFilter) {
        FailureListener f = failureListener;
        if (f != null) {
            f.onSubscriptionError(e, topicFilter);
        }
    }

    private void doMessage(final String topic, final MqttMessage message) {
        callbackExecutor.execute(() -> {
            MessageListener messageListener = this.messageListener;
            String msg = message.toString();
            if (messageListener != null) {
                //  String msg = message.toString(); // of the payload
                messageListener.onMessage(topic, msg);
            }
            // Zusätzliche PressListener → LiveViewerActivity
            for (PressListener l : pressListeners) {
                l.onPress(topic, msg);
            }
        });
    }

    public void setFailureListener(FailureListener failureListener) {
        this.failureListener = failureListener;
    }

    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    public void subscribe(String topicFilter) {
        if (!ready.get() && !connectPending.get()) {
            throw new RuntimeException("connect not yet called");
        }
        subscriptions.add(topicFilter);
        mqttExecutor.execute(() -> {
            try {
                Log.v(TAG, "subscribe: " + topicFilter );
                MqttClient c = client;
                if (c != null && ready.get()) {
                    c.subscribe(topicFilter);
                }
            } catch (MqttException e) {
                Log.e(TAG, String.format("  subscribe failed: topic=%s, cause=%s", topicFilter, e.getMessage()));
                subscriptions.remove(topicFilter);
                doSubscriptionFailure(e, topicFilter);
            }
        });
    }

    public void unsubscribe(String topicFilter) {
        if (!ready.get() && !connectPending.get()) {
            throw new RuntimeException("connect not yet called");
        }
        mqttExecutor.execute(() -> {
            try {
                boolean contained = subscriptions.remove(topicFilter);
                if (contained) {
                    MqttClient c = client;
                    if (c != null && ready.get()) {
                        client.unsubscribe(topicFilter);
                    }
                }
            } catch (MqttException e) {
                // even failed unsubscribe is a subscription failure
                doSubscriptionFailure(e, topicFilter);
            }
        });
    }

    public void send(final String topic, final String msg) {
        if (!ready.get() && !connectPending.get()) {
            throw new RuntimeException("connect not yet called");
        }
        final long id = pendingMessageId.incrementAndGet();
        pendingMessages.put(id, Pair.createPair(topic,  msg));
        mqttExecutor.execute(() -> {
            try {
                pendingMessages.remove(id); // we are processing it
                Log.v(TAG, String.format("send: topic=%s, msg=%s",topic, msg));
                MqttMessage message = new MqttMessage();
                message.setPayload(msg.getBytes());
                message.setQos(1); // we always do 1
                MqttClient c = client;
                if (c != null) {
                    pendingMessages.remove(id);
                    c.publish(topic, message);
                }
            } catch (MqttException e) {
                Log.e(TAG, String.format("  sent failed: topic=%s, msg=%s, cause=%s", topic, msg, e.getMessage()));
                doMessageFailure(e, topic, msg);
            }
        });
    }

    public String getClientId() {
        return clientId;
    }

    public boolean hasPendingMessages() {
        return !pendingMessages.isEmpty();
    }

    public List<Pair<String, String>> disconnect() { // do in onDestroy()
        final MqttClient c = client;
        client = null; // let all pending messaging perish
        if (callbackExecutor != null) {
            List<Runnable> pending = callbackExecutor.shutdownNow();
            // well, if there are incoming messages pending, then
            // you didn't do the callbacks fast enough -> no mercy, ignore
            if (!pending.isEmpty()) {
                Log.w(TAG, String.format("disconnect: %d incoming lost", pending.size()));
            }
            callbackExecutor = null;
        }
        if (c != null && ready.get()) {
            ready.set(false);
            List<Runnable> pending = mqttExecutor.shutdownNow();
            mqttExecutor = null;
            if (!pending.isEmpty()) {
                Log.w(TAG, String.format("disconnect: %d outgoing lost", pending.size()));
            }
            // force that one to be in background
            new Thread(() -> {
                try {
                    c.disconnect();
                    if (connectionListener != null) {
                        connectionListener.onDisconnect();
                    }
                } catch (MqttException ignore) {
                }
            }).start();
        }
        messageListener = null; // do not keep anything
        failureListener = null; // do not keep anything
        return getClearPendingMessages();
    }

    private List<Pair<String, String>> getClearPendingMessages() {
        List<Pair<String, String>> pm = new ArrayList<>(pendingMessages.size());
        pm.addAll(pendingMessages.values());
        pendingMessages.clear();
        return pm;
    }


    //Für free workout
    public void connect(String broker, String username, String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName(username);
        options.setPassword(password.toCharArray());

        connect(broker, options);
    }


    public static MqttMessaging getInstance(Context context,
                                            FailureListener failureListener,
                                            MessageListener messageListener,
                                            ConnectionListener connectionListener) {
        if (instance == null) {
            instance = new MqttMessaging(context, failureListener, messageListener, connectionListener);
        } else {
            instance.setFailureListener(failureListener);
            instance.setMessageListener(messageListener);
            instance.connectionListener = connectionListener;
        }
        return instance;
    }


    public void publish(String topic, String message) {
        try {
            if (client != null && ready.get()) {
                client.publish(topic, message.getBytes(), 0, false);
            }
        } catch (Exception e) {
            Log.e("MQTT", "Fehler beim Publish: " + e.getMessage());
        }
    }



}
