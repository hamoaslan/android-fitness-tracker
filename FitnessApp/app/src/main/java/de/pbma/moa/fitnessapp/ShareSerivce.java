package de.pbma.moa.fitnessapp;

import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.time.LocalDate;
import java.util.List;

import database.AppDataBase;
import database.StepsDAO;
import database.StepsEntity;
import de.pbma.moa.fitnessapp.mqtt.MqttMessaging;
import de.pbma.moa.fitnessapp.mqtt.Util;

public class ShareSerivce extends Service {
    private static final String TAG="ShareService";
    public static final String ACTION_START="start";
    public static final String ACTION_STOP = "stop";
    private static boolean dbg_stop=false;
    public static boolean SHARING=false;
    private MqttMessaging mqttMessaging;
    private boolean connected;
    private String topic;
    private AppDataBase appDataBase;
    private StepsDAO stepsDAO;
    private LiveData<StepsEntity> liveTodaySteps;
    private final MqttMessaging.MessageListener messageListener= new MqttMessaging.MessageListener() {
        @Override
        public void onMessage(String topic, String msg) {
            Log.v(TAG,"onMessage: "+msg);
        }
    };
    private final MqttMessaging.ConnectionListener connectionListener=new MqttMessaging.ConnectionListener() {
        @Override
        public void onConnect() {
            Log.v(TAG,"onConnect");
            connected=true;
        }

        @Override
        public void onDisconnect() {
            Log.v(TAG,"onDisconnect");
            connected=false;
        }
    };
    private final MqttMessaging.FailureListener failureListener=new MqttMessaging.FailureListener() {
        @Override
        public void onConnectionError(Throwable throwable) {
            Log.e(TAG,"onConnectionError",throwable);
            connected=false;
        }

        @Override
        public void onMessageError(Throwable throwable, String msg) {
            Log.e(TAG,"onMessageError"+msg,throwable);
            connected=false;
        }

        @Override
        public void onSubscriptionError(Throwable throwable, String topic) {
            Log.e(TAG,"onSubscriptionError"+topic,throwable);
            connected=false;
        }
    };
    private ConnectivityManager connectivityManager;
    public class LocalBinder extends Binder {
        ShareSerivce getMQTTService() {
            return ShareSerivce.this;
        }
    }
    final private IBinder localBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public void onCreate() {
        handler.post(this::alive);
        super.onCreate();
        Log.v(TAG, "onCreate");
        connectivityManager=getSystemService(ConnectivityManager.class);
        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.v(TAG,"onAvailable");
                connect();
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                Log.v(TAG,"onLost");
            }
        });
        SHARING=true;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG,"onDestroy");
        super.onDestroy();
        disconnect();
        if(liveTodaySteps!=null){
            liveTodaySteps.removeObserver(observer);
        }
        dbg_stop=true;
        SHARING=false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        String action=null;
        if (intent != null) {
            action = intent.getAction();
        }
        if (action == null) {
            Log.w(TAG, "  action=null, nothing further to do");
            return START_STICKY;
        }
        switch (action) {
            case ACTION_START:
                Log.v(TAG, "onStartCommand: starting MQTT");
                connect();
                initDatenbank();
                topic=intent.getStringExtra("topic");
                return START_STICKY;
            case ACTION_STOP:
                Log.v(TAG, "onStartCommand: stopping MQTT");
                assert topic!=null;
                mqttMessaging.send(topic,"stop");
                stopSelf();
                // whatever else needs to be done on stop may be done  here
                return START_NOT_STICKY;
            default:
                Log.w(TAG, "onStartCommand: unkown action=" + action);
                return START_NOT_STICKY;
        }
    }

    private void connect() {
        Log.v(TAG, "connect");
        if (mqttMessaging != null) {
            disconnect();
            Log.w(TAG, "reconnect");
        }
        String connectionURL = Util.fmt("%s://%s:%d", "ssl", "mqtt.inftech.hs-mannheim.de", 8883);
        mqttMessaging = new MqttMessaging(failureListener, messageListener, connectionListener);
        Log.v(TAG, "connectionURL=" + connectionURL+", ");
        MqttConnectOptions options = MqttMessaging.getMqttConnectOptions();
        String username = "25moagd";
        options.setUserName(username);
        String password = "3ada222c";
        options.setPassword(password.toCharArray());
        Log.v(TAG, String.format("username=%s, password=%s, ", username, password));
        if(mqttMessaging!=null){
            mqttMessaging.connect(connectionURL, options); // secure via URL
        }
    }
    private void disconnect() {
        Log.v(TAG, "disconnect");
        if (mqttMessaging != null) {
            List<MqttMessaging.Pair<String, String>> pending = mqttMessaging.disconnect();
            if (!pending.isEmpty()) {
                Log.w(TAG, "pending messages: " + pending.size());
            }
        }
        mqttMessaging = null;
        connected=false;
    }

    public void initDatenbank(){
        Log.v(TAG,"initDatenbank");
        appDataBase=AppDataBase.getInstance(this);
        stepsDAO=appDataBase.stepsDAO();
        liveTodaySteps=stepsDAO.getLiveData(LocalDate.now().toString());
        liveTodaySteps.observeForever(observer);
    }
    private Observer<StepsEntity> observer=new Observer<StepsEntity>() {
        @Override
        public void onChanged(StepsEntity stepsEntity) {
            if(stepsEntity == null){
                return;
            }
            Log.v(TAG,"onChanged");
            if(connected && topic!=null){
                mqttMessaging.send(topic, String.valueOf(stepsEntity.steps));
            }
        }
    };
    private Handler handler=new Handler(Looper.getMainLooper());
    private void alive(){
        Log.v(TAG,"alive");
        if(!dbg_stop){
            handler.postDelayed(this::alive,2000);
        }
    }

}
