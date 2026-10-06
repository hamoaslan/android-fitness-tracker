package de.pbma.moa.fitnessapp;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.pbma.moa.fitnessapp.mqtt.MqttMessaging;
import de.pbma.moa.fitnessapp.mqtt.Util;

public class ShareActivity extends AppCompatActivity {
    private static final String TAG="ShareActivity";
    private String adress;
    private Button share;
    private Button subscribe;
    private LinearLayout scrollView;
    private ViewGroup.LayoutParams layoutParams=new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    private ImageView imageView;
    private Map<String,TextView> sharer=new HashMap<>();
    private List<String> sharer_topic=new ArrayList<>();
    private ShareSerivce mqttService;
    private boolean mqttServiceBound;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            mqttService = ((ShareSerivce.LocalBinder) service).getMQTTService();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // unintentionally disconnected
            Log.v(TAG, "onServiceDisconnected");
            unbindShareService(); // cleanup, the same as intentionally disconnected
        }
    };
    private MqttMessaging mqttMessaging;
    private boolean connected;
    private ConnectivityManager connectivityManager;
    private String topic;
    private final MqttMessaging.MessageListener messageListener= new MqttMessaging.MessageListener() {
        @Override
        public void onMessage(String topic, String msg) {
            Log.v(TAG,"onMessage: "+msg);
            TextView textView=sharer.get(topic);
            if(textView!=null){
                String text=String.valueOf(textView.getText());
                String[] texte=text.split(" ");
                String txt=texte[0]+" "+msg;
                ShareActivity.this.runOnUiThread(()->textView.setText(txt));
            }
            if(msg.equals("stop")){
                Log.v(TAG,"onMsgStop");
                ShareActivity.this.runOnUiThread(()->delete(topic));
            }
        }
        private void delete(String topic){
            scrollView.removeView(sharer.get(topic));
            mqttMessaging.unsubscribe(topic);
            sharer.remove(topic);
            sharer_topic.remove(topic);
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
            //reconnect
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
    @SuppressLint("SetTextI18n")
    private void onClickShare(View view){
        if(share.getText().equals("SHARE")){
            share.setText("STOP");
            topic="25moagd/"+ adress;
            try{
                BarcodeEncoder barcodeEncoder=new BarcodeEncoder();
                Bitmap bitmap=barcodeEncoder.encodeBitmap(Build.DEVICE+" "+topic, BarcodeFormat.QR_CODE,400,400);
                imageView.setImageBitmap(bitmap);
            }catch (Exception e){
                Log.e(TAG,"onStartService",e);
            }
            Log.v(TAG, "onStartService");
            bindShareService();
            Intent intent = new Intent(this, ShareSerivce.class);
            intent.setAction(ShareSerivce.ACTION_START);
            intent.putExtra("topic",topic);
            startService(intent);
        }
        else{
            share.setText("SHARE");
            imageView.setImageBitmap(null);
            Log.v(TAG, "onStopService");
            Intent intent = new Intent(this, ShareSerivce.class);
            intent.setAction(ShareSerivce.ACTION_STOP);
            startService(intent);
            unbindShareService();
        }
    }
    private void onClickSubscribe(View view){
        Log.v(TAG,"onClickSubscribe");
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scanne QR Code");
        integrator.setOrientationLocked(true);
        integrator.setCameraId(0);
        integrator.setBeepEnabled(false);
        integrator.setBarcodeImageEnabled(true);
        integrator.initiateScan();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() == null) {
                Toast.makeText(this, "Scan abgebrochen", Toast.LENGTH_LONG).show();
            } else {
                String[] res=result.getContents().split(" ");
                if(!sharer_topic.contains(res[1]) && connected){
                    mqttMessaging.subscribe(res[1]);
                    sharer_topic.add(res[1]);
                    TextView textView=new TextView(this);
                    textView.setLayoutParams(layoutParams);
                    textView.setClickable(true);
                    textView.setOnClickListener(v -> {
                        scrollView.removeView(v);
                        for(Map.Entry<String,TextView> entry: sharer.entrySet()){
                            if(entry.getValue()==v){
                                mqttMessaging.unsubscribe(entry.getKey());
                                sharer.remove(entry.getKey());
                                sharer_topic.remove(entry.getKey());
                            }
                        }
                    });
                    String txt=res[0]+" "+"0";
                    textView.setText(txt);
                    scrollView.addView(textView);
                    sharer.put(res[1],textView);
                }

            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectivityManager=getSystemService(ConnectivityManager.class);
        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
            @Override
            public void onLost(@NonNull Network network) {
                Log.v(TAG,"onLost");
                TextView text=new TextView(ShareActivity.this);
                text.setText("NO INTERNET");
                ShareActivity.this.runOnUiThread(()->setContentView(text));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                ShareActivity.this.runOnUiThread(()->setup());
            }
        });
        setup();
    }
    private void setup(){
        Log.v(TAG, "onSetup");
        Network network=connectivityManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities=connectivityManager.getNetworkCapabilities(network);
        if(network==null || networkCapabilities==null)
        {
            Log.v(TAG,"noNetwork/Capabilities");
            TextView text=new TextView(this);
            text.setText("NO INTERNET");
            setContentView(text);
            return;
        }
        if(!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)){
            Log.v(TAG,"noInternet");
            Log.v(TAG,networkCapabilities.toString());
            TextView text=new TextView(this);
            text.setText("NO INTERNET");
            setContentView(text);
            return;
        }
        setContentView(R.layout.share_layout);
        //Build
        adress=Build.FINGERPRINT;
        //Buttons
        share=findViewById(R.id.btn_share);
        subscribe=findViewById(R.id.btn_subscribe);
        share.setText("SHARE");
        share.setOnClickListener(this::onClickShare);
        subscribe.setOnClickListener(this::onClickSubscribe);

        //Image
        imageView=findViewById(R.id.qrCodeImage);
        //Text
        scrollView=findViewById(R.id.ll_scrollView);
        //MQTT
        connect();
        if(ShareSerivce.SHARING){
            share.setText("STOP");
            topic="25moagd/"+ adress;
            try{
                BarcodeEncoder barcodeEncoder=new BarcodeEncoder();
                Bitmap bitmap=barcodeEncoder.encodeBitmap(Build.DEVICE+" "+topic, BarcodeFormat.QR_CODE,400,400);
                imageView.setImageBitmap(bitmap);
            }catch (Exception e){
                Log.e(TAG,"onSetup",e);
            }
            bindShareService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG,"onDestroy");
        disconnect();
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
        mqttMessaging.connect(connectionURL, options); // secure via URL
        for(String topic: sharer_topic){
            mqttMessaging.subscribe(topic);
        }
    }
    private void disconnect() {
        Log.v(TAG, "disconnect");
        if (mqttMessaging != null) {
            for(String topic: sharer_topic){
                mqttMessaging.unsubscribe(topic);
            }
            List<MqttMessaging.Pair<String, String>> pending = mqttMessaging.disconnect();
            if (!pending.isEmpty()) {
                Log.w(TAG, "pending messages: " + pending.size());
            }
        }
        mqttMessaging = null;
        connected=false;
    }

    private void bindShareService() {
        Log.v(TAG, "bindShareService");
        Intent intent = new Intent(this, ShareSerivce.class);
        mqttServiceBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!mqttServiceBound) {
            Log.e(TAG, "could not try to bind service, will not be bound");
        }
    }

    private void unbindShareService() {
        Log.v(TAG, "unbindShareService");
        if (mqttServiceBound) {
            mqttServiceBound = false;
            unbindService(serviceConnection);
        }
    }
}
