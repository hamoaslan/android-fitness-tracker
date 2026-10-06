package de.pbma.moa.fitnessapp.helpers;

import android.widget.ProgressBar;
import android.widget.TextView;

public class MyProgressBar {
    private ProgressBar Bar;
    private TextView Bar_txt;
    private String Key;
    private int max;
    private int cur;
    private String unit;
    private String name;
    public MyProgressBar(String name,String unit,ProgressBar bar,TextView txt){
        this.name=name;
        this.unit=unit;
        this.Bar=bar;
        this.Bar_txt=txt;
        this.max=0;
        this.cur=0;
    }
    public void updateProgress(){
        Bar_txt.setText(name+": "+cur+"/"+max+unit);
    }

    public ProgressBar getBar() {
        return Bar;
    }

    public void setBar(ProgressBar bar) {
        Bar = bar;
    }

    public void setBar_txt(TextView bar_txt) {
        Bar_txt = bar_txt;
    }
    public TextView getBar_txt(){
        return Bar_txt;
    }

    public String getKey() {
        return Key;
    }

    public void setKey(String key) {
        Key = key;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setCur(int cur) {
        this.Bar.setProgress(cur);
        this.cur = cur;
        this.updateProgress();
    }

    public void setMax(int max) {
        this.Bar.setMax(max);
        this.max = max;
        this.updateProgress();
    }
    public int getMax(){
        return max;
    }
    public int getCur(){
        return cur;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
