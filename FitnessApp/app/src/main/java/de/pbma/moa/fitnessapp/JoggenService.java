package de.pbma.moa.fitnessapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.*;

import database.AppDataBase;
import database.GpsDAO;
import database.GpsEntity;

public class JoggenService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String routeName;
    private static final String TAG="JoggenService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");
        createNotificationChannel();
    }


    /*
        Lokaler notification channel zum übertragen der Locations mittels Broadcasts and die JoggenActivity
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Jogging Notifications";
            String description = "Notifications for jogging tracking service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("jogging_channel", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /*
        Locations tracking start im Hintergrund und Speichern der einzelnen Punkte
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        routeName = intent.getStringExtra("routeName");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateDistanceMeters(3.0f)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    broadcastLocation(location);
                    saveToDatabase(location);
                }
            }
        };
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, null);
        }


        startForeground(1, new NotificationCompat.Builder(this, "jogging_channel")
                .setContentTitle("Jogging Tracker")
                .setContentText("Tracking in background...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build()
        );

        return START_STICKY;
    }

    private void broadcastLocation(Location location) {
        Intent intent = new Intent("de.pbma.moa.fitnessapp.NEW_GPS_POINT");
        intent.putExtra("latitude", location.getLatitude());
        intent.putExtra("longitude", location.getLongitude());
        intent.putExtra("time", location.getTime());
        intent.putExtra("speed", location.getSpeed());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void saveToDatabase(Location location) {
        AppDataBase db = AppDataBase.getInstance(getApplicationContext());
        GpsDAO dao = db.gpsDAO();
        GpsEntity entity = new GpsEntity();
        entity.routename = routeName;
        entity.latitude = location.getLatitude();
        entity.longitude = location.getLongitude();
        entity.time = location.getTime();
        entity.speed = location.getSpeed();
        new Thread(() -> dao.insert(entity)).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
