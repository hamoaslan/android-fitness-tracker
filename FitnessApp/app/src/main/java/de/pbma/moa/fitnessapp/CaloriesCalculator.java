package de.pbma.moa.fitnessapp;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class CaloriesCalculator extends AppCompatActivity {
    private static final String TAG="CaloriesCalculator";
    private int weight;
    private int height;
    private int goal_weight;
    private int age;
    private int calories;
    private int fat;
    private int protein;
    private int carbohydrate;
    private String gender;
    private Button calculate;
    private Button save;
    private TextView gender_txt;
    private TextView weight_txt;
    private TextView heigth_txt;
    private TextView goal_weight_txt;
    private TextView age_txt;
    private TextView sug_calories;
    private TextView sug_fat;
    private TextView sug_protein;
    private TextView sug_carbohydrates;
    private SharedPreferences preferences;
    private void onClickCalculate(View view){
        switch (gender){
            case "Man":
                calcCalories(5,1f);
                save.setVisibility(View.VISIBLE);
                break;
            case "Woman":
                calcCalories(-161,0.9f);
                save.setVisibility(View.VISIBLE);
                break;
            case "Divers":
                calcCalories(-100,0.95f);
                save.setVisibility(View.VISIBLE);
                break;
            case "Tie-Fighter":
                sug_calories.setText("ARAL TANKSTELLE");
                break;
            default:
                sug_calories.setText("???");
                break;
        }
    }
    private void calcCalories(int offset,float factor){
        calories= (int) (10*weight+6.25*height-5*age+offset);
        calories= (int) (calories*1.55);
        if(goal_weight>weight) {
            calories += 250;
        }
        if(goal_weight<weight) {
            calories -= 500;
        }
        protein= (int) (2*weight*factor);
        fat= (int) (weight*factor);
        carbohydrate=calories/8;

        String sug_caloriesStr="Suggested Calories: "+calories+"kcal";
        String sug_fatStr="Suggested Fat: "+fat+"g";
        String sug_proteinStr="Suggested Protein: "+protein+"g";
        String sug_carbohydratesStr="Suggested Carbohydrates: "+carbohydrate+"g";

        sug_calories.setText(sug_caloriesStr);
        sug_protein.setText(sug_proteinStr);
        sug_fat.setText(sug_fatStr);
        sug_carbohydrates.setText(sug_carbohydratesStr);
    }
    private void onClickSave(View view){
        SharedPreferences.Editor editor=preferences.edit();
        Resources resources=getResources();
        String keyCalories=resources.getString(R.string.key_calories);
        String keyFat=resources.getString(R.string.key_fat);
        String keyProtein=resources.getString(R.string.key_protein);
        String keyCarbohydrates=resources.getString(R.string.key_carbohydrates);
        editor.putString(keyCalories,""+calories);
        editor.putString(keyFat,""+protein);
        editor.putString(keyProtein,""+fat);
        editor.putString(keyCarbohydrates,""+carbohydrate);
        editor.apply();
        finish();
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calories_calc_layout);

        gender_txt=findViewById(R.id.gender_txt);
        weight_txt=findViewById(R.id.weight_txt);
        heigth_txt=findViewById(R.id.height_txt);
        goal_weight_txt=findViewById(R.id.goal_weight_txt);
        age_txt=findViewById(R.id.age_txt);
        sug_calories=findViewById(R.id.sug_calories);
        sug_fat=findViewById(R.id.sug_fat);
        sug_protein=findViewById(R.id.sug_protein);
        sug_carbohydrates=findViewById(R.id.sug_carbohydrates);
        calculate=findViewById(R.id.btn_calculate);
        save=findViewById(R.id.btn_save);
        calculate.setOnClickListener(this::onClickCalculate);
        save.setOnClickListener(this::onClickSave);

        setupPreferences();

        String weightStr="Weight: "+ weight;
        String heightStr="Height: "+ height;
        String goal_weightStr="Zielgewicht: "+ goal_weight;
        String ageStr="Age: "+ age;
        String genderStr="Gender: "+gender;
        weight_txt.setText(weightStr);
        heigth_txt.setText(heightStr);
        goal_weight_txt.setText(goal_weightStr);
        age_txt.setText(ageStr);
        gender_txt.setText(genderStr);
    }

    private void setupPreferences(){
        PreferenceManager.setDefaultValues(this,R.xml.foodpreferences,false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        //preferences.registerOnSharedPreferenceChangeListener(opscl);
        Resources resources=getResources();
        String weightKey=resources.getString(R.string.key_weight);
        weight=Integer.parseInt(preferences.getString(weightKey,"42"));
        String heigtKey=resources.getString(R.string.key_height);
        height=Integer.parseInt(preferences.getString(heigtKey,"42"));
        String goal_weightKey=resources.getString(R.string.key_goal_weight);
        goal_weight=Integer.parseInt(preferences.getString(goal_weightKey,"80"));
        String ageKey=resources.getString(R.string.key_age);
        age=Integer.parseInt(preferences.getString(ageKey,"18"));
        String genderKey=resources.getString(R.string.key_sex);
        gender=preferences.getString(genderKey,"Man");
    }
}
