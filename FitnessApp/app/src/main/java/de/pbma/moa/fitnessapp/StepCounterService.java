package de.pbma.moa.fitnessapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.time.LocalDate;

import database.AppDataBase;
import database.StepsDAO;
import database.StepsEntity;

public class StepCounterService extends Service implements SensorEventListener {
    private static final String TAG="StepCounterService";
    private SensorManager sensorManager;
    private Sensor steps;

    private AppDataBase appDataBase;
    private StepsDAO stepsDAO;
    private int stepcnt=0;
    private int steps_today=0;
    private String day=LocalDate.now().toString();
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG,"onBind");
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            Log.v(TAG,"onSensorChanged");
            int val=(int) event.values[0];
            stepcnt+=val;
            saveSteps("alltime", stepcnt);
            if (checkDay()) {
                steps_today += val;
            } else {
                changeDay();
            }
            saveSteps(day, steps_today);
        } catch (Exception e) {
            Log.e(TAG,"DB FAILED",e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void initSensor(){
        Log.v(TAG,"initSensor");
        sensorManager=(SensorManager) getSystemService(Context.SENSOR_SERVICE);
        steps=sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sensorManager.registerListener(this,steps,SensorManager.SENSOR_DELAY_NORMAL);
        if(steps==null){
            Log.v(TAG,"No Sensor");
            stepcnt=42017;
        }
    }
    public void initDatenbank(){
        Log.v(TAG,"initDatenbank");
        appDataBase=AppDataBase.getInstance(this);
        stepsDAO=appDataBase.stepsDAO();
        new Thread(){
            public void run(){
                stepcnt=stepsDAO.getByType("alltime",true).steps;
                steps_today=stepsDAO.getByType(day,true).steps;
            }
        }.start();
    }
    public void saveSteps(String type,int step){
        Log.v(TAG,"saveSteps");
        StepsEntity steps=new StepsEntity();
        steps.steps=step;
        steps.type=type;
        steps.time=System.nanoTime();
        new Thread(){
            @Override
            public void run() {
                stepsDAO.update(steps);
            }
        }.start();
    }
    public boolean checkDay(){
        return day.equals(LocalDate.now().toString());
    }
    public void changeDay(){
        day=LocalDate.now().toString();
        steps_today=1;
        StepsEntity steps=new StepsEntity();
        steps.steps=steps_today;
        steps.type=day;
        steps.time=System.nanoTime();
        new Thread(){
            @Override
            public void run() {
                stepsDAO.insert(steps);
            }
        }.start();
    }
    @Override
    public void onCreate() {
        super.onCreate();
        handler.postAtTime(this::alive,2000);
        Log.v(TAG,"onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG,"onStartCommand");
        //Sensor
        initSensor();

        //Datenbank
        initDatenbank();
        try{
            NotificationChannel channel=new NotificationChannel("100","Test", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("This is a test");
            NotificationManager notificationManager=getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Notification notification=new NotificationCompat.Builder(this,"100").build();
            startForeground(100,notification);
        }catch (Exception e){
            Log.e(TAG,"onStartCommand",e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG,"onDestroy");
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        super.onTimeout(startId, fgsType);
        saveSteps("alltime",stepcnt);
        stopSelf();
    }
    private Handler handler=new Handler(Looper.getMainLooper());
    private void alive(){
        Log.v(TAG,"alive");
        //handler.postDelayed(this::alive,2000);
    }
}
