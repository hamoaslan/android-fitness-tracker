package de.pbma.moa.fitnessapp;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.pm.PackageManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import database.AppDataBase;
import database.GpsDAO;
import database.GpsEntity;

public class JoggenActivity extends AppCompatActivity {
    private static final String TAG="JoggenActivity";
    private Button startButton, endButton, centerButton;
    private TextView titleView, progressView;
    private EditText editText;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private List<GeoPoint> geoPoints = new ArrayList<>();
    private MapView mapView;
    private IMapController iMapController;
    private MapController mapController;
    private LocationRequest locationRequest;
    private Polyline routeLine;
    private boolean autoCenter;
    private AppDataBase appDataBase;
    private GpsDAO gpsDao;
    private String routeName;
    private Spinner routeSpinner;
    private ArrayAdapter<String> spinnerAdapter;
    private Marker endMarker;
    private Marker startMarker;
    private Marker currentMarker;
    private List<Float> avgSpeedLis = new ArrayList<>();
    private List<Long> ranTimeLis = new ArrayList<>();
    private boolean running = true;

    private View.OnClickListener onStartListener = new View.OnClickListener() {
        @Override public void onClick(View view){
            onClickStartTracker(view);
        }
    };
    public void onClickStartTracker(View view){
        Log.v(TAG, "onClickStartCenter");
        System.out.println("onClickStartTracker");

        routeName = editText.getText().toString();
        if (routeName.isEmpty()){
            titleView.setText("Please enter a route name");
            return;
        }
        checkRouteExist();
    }
    private View.OnClickListener onEndListener = new View.OnClickListener() {
        @Override public void onClick(View view){onClickEndTracker(view);}
    };

    public void onClickEndTracker(View view){
        Log.v(TAG, "onClickEndCenter");
        System.out.println("onClickEndTracker");
        endTracker();
    }
    private View.OnClickListener onAutoCenterListener = new View.OnClickListener() {
        @Override public void onClick(View view){onClickAutoCenter(view);}
    };
    public void onClickAutoCenter(View view){
        Log.v(TAG, "onClickAutoCenter");
        autoCenter = !autoCenter;
        if (autoCenter) {
            centerButton.setText("Auto Center : ON");
        }else{
            centerButton.setText("Auto Center : OFF");
        }
    }

    AdapterView.OnItemSelectedListener avoisl = new AdapterView.OnItemSelectedListener() {
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Log.v(TAG, "onItemSelected");
            if (position == 0){
                return;
            }
            String route = (String) parent.getSelectedItem();
            loadRoute(route);
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    };

    @Override
    protected  void onCreate(Bundle SavedInstanceState){
        Log.v(TAG, "onCreate");
        super.onCreate(SavedInstanceState);

        File osmdroidBasePatch = new File(getCacheDir(), "osmdroid");
        File osmdroidTileCache = new File(osmdroidBasePatch, "tile");
        Configuration.getInstance().setOsmdroidBasePath(osmdroidBasePatch);
        Configuration.getInstance().setOsmdroidTileCache(osmdroidTileCache);

        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(
                PreferenceManager.getDefaultSharedPreferencesName(ctx), MODE_PRIVATE);
        Configuration.getInstance().load(ctx, prefs);

        setContentView(R.layout.joggen_layout);
        titleView = findViewById(R.id.gps_title_info);
        progressView = findViewById(R.id.gps_progress);

        startButton = findViewById(R.id.gps_start_tracking);
        startButton.setOnClickListener(onStartListener);

        endButton = findViewById(R.id.gps_end_tracking);
        endButton.setOnClickListener(onEndListener);
        endButton.setEnabled(false);

        centerButton = findViewById(R.id.gps_auto_center);
        centerButton.setOnClickListener(onAutoCenterListener);
        autoCenter = true;

        editText = findViewById(R.id.gps_edit);

        routeSpinner = findViewById(R.id.gps_spinner);

        spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>()
        );

        routeSpinner.setAdapter(spinnerAdapter);
        routeSpinner.setOnItemSelectedListener(avoisl);

        appDataBase = AppDataBase.getInstance(this);
        gpsDao = appDataBase.gpsDAO();

        mapView = findViewById(R.id.gps_mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        mapView.setMultiTouchControls(true);
        iMapController = mapView.getController();
        iMapController.setZoom(19.0);
        endMarker = new Marker(mapView);
        startMarker = new Marker(mapView);
        currentMarker = new Marker(mapView);
        currentMarker.setIcon(ResourcesCompat.getDrawable(getResources(), android.R.drawable.ic_menu_mylocation, null));

        routeLine = new Polyline();
        routeLine.setTitle("Route");
        Paint paint = routeLine.getOutlinePaint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(20);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        checkAllPermission();
        loadSpinner();
    }

    // Peremission Check für die location Anfragen
    public void checkAllPermission(){
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    100);
        }
    }

    // Telemetrie Daten updater
    public void progressUpdater(float speed){

        String currentSpeedText = "Current Speed: " + String.format("%.2f", speed)  + " m/s\n";
        String ranTimeText = "Ran time: " + getRanTime() + "\n";
        String avgSpeedText = "Avg speed: " + String.format("%.2f", getAvgSpeed()) + " m/s";
        String newText = currentSpeedText + ranTimeText + avgSpeedText;
        progressView.setText(newText);

    }
    public String getRanTime(){

        long time = ranTimeLis.get(ranTimeLis.size() - 1) - ranTimeLis.get(0);
        Duration duration = Duration.ofMillis(time);
        long h = duration.toHours();
        long m = duration.toMinutes() % 60;
        long s = duration.getSeconds() % 60;

        String ranTime = String.format("%02d:%02d:%02d", h, m, s);

        return ranTime;
    }

    public float getAvgSpeed() {
        float speed = 0.0f;
        for (float val : avgSpeedLis){
            speed += val;
        }
        speed = speed/avgSpeedLis.size();
        return speed;
    }

    /*
        BroadcastReceiver, der bei aktivem App-Fokus Standort-Updates vom Service empfängt und die Route live aktualisiert.
     */
    private final BroadcastReceiver gpsUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "onReceive");
            if (intent.getAction().equals("de.pbma.moa.fitnessapp.NEW_GPS_POINT")) {
                Log.v(TAG, "received broadcast");

                // Location daten
                double lat = intent.getDoubleExtra("latitude", 0.0);
                double lon = intent.getDoubleExtra("longitude", 0.0);
                float speed = intent.getFloatExtra("speed", 0.0f);
                long time = intent.getLongExtra("time", 0L);

                /*  Neuer GeoPoint um die Route zu aktuallisieren
                    Average Speed wird aktuallisiert
                    Gelaufene Zeit wird aktuallisiert
                 */
                GeoPoint newPoint = new GeoPoint(lat, lon);
                geoPoints.add(newPoint);
                avgSpeedLis.add(speed);
                ranTimeLis.add(time);

                // Live Marker der aktuellen Position aktuallisieren
                currentMarker.setPosition(newPoint);
                currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                // Änderungen auf die Map zeichen
                mapView.getOverlays().clear();
                mapView.getOverlays().add(routeLine);
                mapView.getOverlays().add(currentMarker);
                mapView.getOverlays().add(startMarker);
                routeLine.setPoints(geoPoints);

                if (autoCenter) {
                    iMapController.setCenter(newPoint);
                }
                progressUpdater(speed);
                mapView.invalidate();
            }
        }
    };

    /* Start des Tracking.
        - Listen werden geleert
        - Initialer Start Marker
        - Startbutton wird deaktiviert
        - Endbutton wird aktiviert
        - Service für die Locations wird gestartet
    */
    public void startTracker(){
        Log.v(TAG, "startTracker");

        avgSpeedLis.clear();
        ranTimeLis.clear();

        mapView.getOverlays().clear();

        editText.setText("");
        String hint = "Current tracking route: " + routeName;
        editText.setHint(hint);
        editText.setEnabled(false);

        startButton.setEnabled(false);
        geoPoints.clear();

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            GeoPoint newPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                            startMarker.setPosition(newPoint);
                            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            startMarker.setTitle("start");
                            mapView.getOverlays().add(startMarker);
                            if (autoCenter){
                                iMapController.setCenter(newPoint);
                            }
                            mapView.invalidate();
                            new Thread(() ->{
                                databaseEntry(routeName, location.getLongitude(), location.getLatitude(), location.getTime(), location.getSpeed());
                            }).start();
                        }
                    });
        }
        Intent serviceIntent = new Intent(this, JoggenService.class);
        serviceIntent.putExtra("routeName", routeName);
        ContextCompat.startForegroundService(this, serviceIntent);

        endButton.setEnabled(true);
    }

    /* Tracking wird beendet.
        - End Marker wird gestetzt
        - Startbutton wird aktiviert
        - Endbutton wird deaktiviert
        - Service für die Locations wird beendet
        - Routenname wird auf "" gesetzt
    */
    public void endTracker(){
        Log.v(TAG, "endTracker");
        endButton.setEnabled(false);
        Intent serviceIntent = new Intent(this, JoggenService.class);
        stopService(serviceIntent);

        if(!geoPoints.isEmpty()){
            GeoPoint endPoint = geoPoints.get(geoPoints.size() - 1);
            endMarker.setPosition(endPoint);
            endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            endMarker.setTitle("End");
            mapView.getOverlays().add(endMarker);
            mapView.invalidate();
        }
        spinnerAdapter.add(routeName);
        spinnerAdapter.notifyDataSetChanged();

        startButton.setEnabled(true);
        editText.setHint("Enter a route name");
        editText.setEnabled(true);
        routeName = "";
    }

    /*
        Aktuallisiert die Anzeige der gespeicherten Routen.
     */
    public void loadSpinner(){
        Log.v(TAG, "loadSpinner");

        new Thread(()->{
            List<String> routes = gpsDao.getRoutes();

            runOnUiThread(()->{
                spinnerAdapter.clear();
                spinnerAdapter.add("-- Select saved route --");
                spinnerAdapter.addAll(routes);
                routeSpinner.setSelection(0);
                spinnerAdapter.notifyDataSetChanged();
            });

        }).start();

    }

    /*
        Ladet die ausgewählte Route.
     */
    public void loadRoute(String routename) {
        Log.v(TAG, "loadRoute");
        mapView.getOverlays().clear();
        geoPoints.clear();
        avgSpeedLis.clear();
        ranTimeLis.clear();
        new Thread(()->{
                List<GpsEntity> route = gpsDao.getRoute(routename);
                List<Float> tempSpeedLis = new ArrayList<>();
                List<Long> tempTimeLis = new ArrayList<>();
                for (GpsEntity point : route){
                    GeoPoint newGeoPoint = new GeoPoint(point.latitude, point.longitude);
                    geoPoints.add(newGeoPoint);
                    tempSpeedLis.add(point.speed);
                    tempTimeLis.add(point.time);
                }

                runOnUiThread(() -> {
                    avgSpeedLis = tempSpeedLis;
                    ranTimeLis = tempTimeLis;

                    progressUpdater(0f);

                    startMarker.setPosition(geoPoints.get(0));
                    startMarker.setTitle("start");
                    endMarker.setPosition(geoPoints.get(geoPoints.size() - 1));
                    endMarker.setTitle("end");

                    iMapController.setCenter(geoPoints.get(geoPoints.size()/2));
                    iMapController.setZoom(16.0);
                    routeLine.setPoints(geoPoints);
                    mapView.getOverlays().add(startMarker);
                    mapView.getOverlays().add(endMarker);
                    mapView.getOverlays().add(routeLine);
                    mapView.invalidate();
                });
        }).start();
    }
    /*
        Lädt beim Öffnen der App den aktuellen Stand der aktiven Route, nachdem sie im Hintergrund war.
     */
    public void loadOnResume(String routename){
        Log.v(TAG, "loadOnResume");
        geoPoints.clear();
        avgSpeedLis.clear();
        ranTimeLis.clear();

        new Thread(()->{
            List<GpsEntity> route = gpsDao.getRoute(routename);
            List<Float> tempSpeedLis = new ArrayList<>();
            List<Long> tempTimeLis = new ArrayList<>();
            for (GpsEntity point : route){
                GeoPoint newGeoPoint = new GeoPoint(point.latitude, point.longitude);
                geoPoints.add(newGeoPoint);
                tempSpeedLis.add(point.speed);
                tempTimeLis.add(point.time);
            }
        }).start();
    }

    public void databaseEntry(String route, double longitude, double latitude, long time, float speed){
        Log.v(TAG, "databaseEntry");
        GpsEntity gpsEntity = new GpsEntity();
        gpsEntity.routename = route;
        gpsEntity.longitude = longitude;
        gpsEntity.latitude = latitude;
        gpsEntity.time = time;
        gpsEntity.speed = speed;
        gpsDao.insert(gpsEntity);
    }

    // Überprüfung ob eingegebener Routenname schon existiert.
    public void checkRouteExist(){
        Log.v(TAG, "checkRouteExist");
        new Thread(()->{
            int count = gpsDao.checkRoute(routeName);
            System.out.println(count + " thread");

            runOnUiThread(() -> {
                if (count == 0){
                    startTracker();
                }else {
                    titleView.setText("Routname Already exists!");
                }
            });
        }).start();
    }

    @Override
    protected void onDestroy(){
        Log.v(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onPause(){
        Log.v(TAG, "onPause");
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(gpsUpdateReceiver);
    }

    @Override
    protected void onResume(){
        Log.v(TAG, "onResume");
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("de.pbma.moa.fitnessapp.NEW_GPS_POINT");
        LocalBroadcastManager.getInstance(this).registerReceiver(gpsUpdateReceiver, intentFilter);

        loadOnResume(routeName);
    }
}
