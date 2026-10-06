package de.pbma.moa.fitnessapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.RoomDatabase;


import java.time.LocalDate;

import database.AppDataBase;
import database.StepsDAO;
import database.StepsEntity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG="MainActivity";
    private Button joggen;
    private Button workout;
    private Button live;
    private Button stats;
    private Button share;
    private Button food;
    private TextView steps;
    private TextView stepsToday;
    private AppDataBase appDataBase;
    private StepsDAO stepsDAO;
    private LiveData<StepsEntity> liveAllSteps;
    private LiveData<StepsEntity> liveTodaySteps;
    private Observer<StepsEntity> observer=new Observer<StepsEntity>() {

        @Override
        public void onChanged(StepsEntity stepsEntity) {
            if(stepsEntity == null){
                return;
            }

            String today=LocalDate.now().toString();
            String type=stepsEntity.type;
            if(type.equals("alltime")){
                steps.setText(String.valueOf(stepsEntity.steps));
            } else if (type.equals(today)) {
                stepsToday.setText(String.valueOf(stepsEntity.steps));
            }
        }
    };
    private void onClickJoggen(View view){
        Intent intent=new Intent(this,JoggenActivity.class);
        startActivity(intent);
    }
    private void onClickWorkout(View view){

        Intent intent = new Intent(this, FreeWorkout.class);
        startActivity(intent);
    }
    private void onClickLive(View view){
        Intent intent = new Intent(this, LiveViewerActivity.class);
        startActivity(intent);
    }

    private void onClickStats(View view){
        Intent intent=new Intent(this,StatsActivity.class);
        startActivity(intent);
    }
    private void onClickShare(View view){
        Intent intent=new Intent(this,ShareActivity.class);
        startActivity(intent);
    }
    private void onClickFood(View view){
        Intent intent=new Intent(this,FoodActivity.class);
        startActivity(intent);
    }

    public void initDatenbank(){
        Log.v(TAG,"initDatenbank");
        appDataBase=AppDataBase.getInstance(this);
        stepsDAO=appDataBase.stepsDAO();
        liveAllSteps=stepsDAO.getLiveData("alltime");
        liveTodaySteps=stepsDAO.getLiveData(LocalDate.now().toString());
        liveAllSteps.observe(this,observer);
        liveTodaySteps.observe(this,observer);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.v(TAG,"onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            Intent intent=new Intent(this, StepCounterService.class);
            startForegroundService(intent);
        }
        else{
            steps.setText("NO PERMS");
            Log.v(TAG,"onPermissionsDenied");
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.v(TAG,"onCreate");
        super.onCreate(savedInstanceState);



        //View
        setContentView(R.layout.main_layout);

        //Buttons
        joggen=findViewById(R.id.btn_joggen);
        workout=findViewById(R.id.btn_workout);
        live=findViewById(R.id.btn_live);
        stats=findViewById(R.id.btn_stats);
        share=findViewById(R.id.btn_share);
        food=findViewById(R.id.btn_food);
        joggen.setOnClickListener(this::onClickJoggen);
        workout.setOnClickListener(this::onClickWorkout);
        live.setOnClickListener(this::onClickLive);
        stats.setOnClickListener(this::onClickStats);
        share.setOnClickListener(this::onClickShare);
        food.setOnClickListener(this::onClickFood);

        //Steps
        steps=findViewById(R.id.text_steps);
        stepsToday=findViewById(R.id.text_steps_today);

        //Service
        String[] help= new String[]{Manifest.permission.ACTIVITY_RECOGNITION};
        ActivityCompat.requestPermissions(this, help,100);

        //Datenbank
        initDatenbank();
    }
}
