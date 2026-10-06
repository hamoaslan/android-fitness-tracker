package de.pbma.moa.fitnessapp;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.time.LocalDate;
import java.util.ArrayList;

import database.AppDataBase;
import database.StepsDAO;
import database.StepsEntity;

public class StatsActivity extends AppCompatActivity {
    private static final String TAG="StatsActivity";
    LineChart lineChart;
    ArrayList<Entry> entries=new ArrayList<>();
    private AppDataBase appDataBase;
    private StepsDAO stepsDAO;
    private LiveData<StepsEntity> liveTodaySteps;
    private Observer<StepsEntity> observer=new Observer<StepsEntity>() {
        @Override
        public void onChanged(StepsEntity stepsEntity) {
            entries.remove(6);
            entries.add(new Entry(6,stepsEntity.steps));
            LineDataSet dataSet = new LineDataSet(entries,null);
            dataSet.setColor(Color.WHITE);

            dataSet.setValueTextColor(Color.WHITE);

            LineData lineData = new LineData(dataSet);
            lineChart.setData(lineData);
            lineChart.invalidate();
        }
    };
    public void initDatenbank(){
        Log.v(TAG,"initDatenbank");
        appDataBase=AppDataBase.getInstance(this);
        stepsDAO=appDataBase.stepsDAO();
        liveTodaySteps=stepsDAO.getLiveData(LocalDate.now().toString());
        liveTodaySteps.observe(this,observer);
    }
    private void configLineChart(){

        ArrayList<String> xLabels = new ArrayList<>();
        for(int i=6; i>1; i--){
            xLabels.add(LocalDate.now().getDayOfWeek().minus(i).toString());
        }
        xLabels.add("Yesterday");
        xLabels.add("Today");
        lineChart.getLegend().setEnabled(false);
        XAxis xAxis = lineChart.getXAxis();
        lineChart.getAxisLeft().setTextColor(Color.WHITE);
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        String[] finalXLabels = xLabels.toArray(new String[0]);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < finalXLabels.length) {
                    return finalXLabels[index];
                } else {
                    return "";
                }
            }
        });
        lineChart.invalidate();
    }

    private void setData(){
        new Thread() {
            @Override
            public void run() {
                int[] data = new int[7];
                for(int i = 0;i<7;i++) {
                    int steps = stepsDAO.getByType(String.valueOf(LocalDate.now().minusDays(6 - i)), true).steps;
                    data[i] = steps;
                }
                for(int i=0; i<7; i++){
                    entries.add(new Entry(i,data[i]));
                }

                LineDataSet dataSet = new LineDataSet(entries,null);
                dataSet.setColor(Color.WHITE);

                dataSet.setValueTextColor(Color.WHITE);

                LineData lineData = new LineData(dataSet);
                lineChart.setData(lineData);
                lineChart.invalidate();
            }
        }.start();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stats_layout);

        lineChart = findViewById(R.id.lineChart);
        configLineChart();
        initDatenbank();
        setData();
    }
}
