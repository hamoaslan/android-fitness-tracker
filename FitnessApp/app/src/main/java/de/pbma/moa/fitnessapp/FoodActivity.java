package de.pbma.moa.fitnessapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.time.LocalDate;
import java.util.List;

import database.AppDataBase;
import database.FoodDAO;
import database.FoodEntity;
import database.ProductDAO;
import database.ProductEntity;
import de.pbma.moa.fitnessapp.foodAPI.FoodResponse;
import de.pbma.moa.fitnessapp.foodAPI.Nutriments;
import de.pbma.moa.fitnessapp.foodAPI.Product;
import de.pbma.moa.fitnessapp.foodAPI.RetrofitAPI;
import de.pbma.moa.fitnessapp.helpers.MyProgressBar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FoodActivity extends AppCompatActivity {
    private static final String TAG="FoodActivity";
    private Button scan;
    private MyProgressBar caloriesBar;
    private MyProgressBar proteinBar;
    private MyProgressBar fatBar;
    private MyProgressBar carbohydratesBar;
    private SharedPreferences preferences;
    private AppDataBase appDataBase;
    private FoodDAO foodDAO;
    private FoodEntity food;
    private LiveData<FoodEntity> liveDateFood;
    private Observer<FoodEntity> observer=new Observer<FoodEntity>() {
        @Override
        public void onChanged(FoodEntity food) {
            if(food==null){
                return;
            }
            FoodActivity.this.runOnUiThread(()->updateProgress(food));
        }
    };

    private void onClickScan(View view){
        Log.v(TAG,"onClickScan");
        IntentIntegrator integrator = new IntentIntegrator(FoodActivity.this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("Scanne den Barcode");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.v(TAG,"onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() == null) {
                Toast.makeText(this, "Scan abgebrochen", Toast.LENGTH_SHORT).show();
            } else {
                String barcode = result.getContents();
                getFoodData(barcode);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.v(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.food_layout);
        //Button
        scan=findViewById(R.id.btn_food);
        scan.setOnClickListener(this::onClickScan);

        //Progressbar
        caloriesBar=new MyProgressBar("Cals","kcal",findViewById(R.id.calories_bar),findViewById(R.id.calories_bar_txt));
        caloriesBar.setCur(0);
        proteinBar=new MyProgressBar("Protein","g",findViewById(R.id.protein_bar),findViewById(R.id.protein_bar_txt));
        proteinBar.setCur(0);
        fatBar=new MyProgressBar("Fat","g",findViewById(R.id.fat_bar),findViewById(R.id.fat_bar_txt));
        fatBar.setCur(0);
        carbohydratesBar=new MyProgressBar("Carbs","g",findViewById(R.id.carbohydrates_bar),findViewById(R.id.carbohydrates_bar_txt));
        carbohydratesBar.setCur(0);

        //Preferences
        setupPreferences();

        //Database
        initDatabase();
    }
    private void getFoodData(String barcode){
        Log.v(TAG,"getFoodData");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://world.openfoodfacts.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitAPI api = retrofit.create(RetrofitAPI.class);

        Call<FoodResponse> call = api.getProduct(barcode);

        call.enqueue(new Callback<FoodResponse>() {
            @Override
            public void onResponse(Call<FoodResponse> call, Response<FoodResponse> response) {
                Log.v(TAG,"onResponse");
                if (response.isSuccessful() && response.body() != null) {
                    Log.v(TAG,"ProductFound");
                    Product product = response.body().getProduct();
                    if (product != null) {
                        String name = product.getProduct_name();
                        String brand = product.getBrands();
                        Nutriments nutriments = product.getNutriments();
                        Gson gson=new Gson();
                        String info=gson.toJson(product);

                        Intent intent=new Intent(FoodActivity.this,FoodServingActivity.class);
                        intent.putExtra("INFO",info);
                        startActivity(intent);
                    }
                    else{
                        Log.v(TAG,"Product null");
                        Toast.makeText(FoodActivity.this, "Produkt null", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.v(TAG,"noProduct");
                    Toast.makeText(FoodActivity.this, "Produkt nicht gefunden", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FoodResponse> call, Throwable t) {
                Log.v(TAG,"onFailure",t);
                Toast.makeText(FoodActivity.this, "Fehler: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }
    private SharedPreferences.OnSharedPreferenceChangeListener opscl=new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
            if(key==null){
                return;
            }
            if(key.equals(caloriesBar.getKey())){
                caloriesBar.setMax(Integer.parseInt(preferences.getString(caloriesBar.getKey(),"2500")));
            }
            if(key.equals(proteinBar.getKey())){
                proteinBar.setMax(Integer.parseInt(preferences.getString(proteinBar.getKey(),"100")));
            }
            if(key.equals(fatBar.getKey())){
                fatBar.setMax(Integer.parseInt(preferences.getString(fatBar.getKey(),"80")));
            }
            if(key.equals(carbohydratesBar.getKey())){
                carbohydratesBar.setMax(Integer.parseInt(preferences.getString(carbohydratesBar.getKey(),"200")));
            }
        }
    };
    private void setupPreferences(){
        PreferenceManager.setDefaultValues(this,R.xml.foodpreferences,false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(opscl);
        Resources resources=getResources();
        caloriesBar.setKey(resources.getString(R.string.key_calories));
        caloriesBar.setMax(Integer.parseInt(preferences.getString(caloriesBar.getKey(),"2500")));
        proteinBar.setKey(resources.getString(R.string.key_protein));
        proteinBar.setMax(Integer.parseInt(preferences.getString(proteinBar.getKey(),"100")));
        fatBar.setKey(resources.getString(R.string.key_fat));
        fatBar.setMax(Integer.parseInt(preferences.getString(fatBar.getKey(),"80")));
        carbohydratesBar.setKey(resources.getString(R.string.key_carbohydrates));
        carbohydratesBar.setMax(Integer.parseInt(preferences.getString(carbohydratesBar.getKey(),"200")));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.foodmenu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId()==R.id.menu_preferences){
            Intent intent=new Intent(this,FoodSetting.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId()==R.id.food_list) {
            Intent intent=new Intent(this, FoodListActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId()==R.id.reset) {
            food.kcal=0;
            food.carbohydrates=0;
            food.fat=0;
            food.carbohydrates=0;
            new Thread(()->foodDAO.update(food)).start();
            new Thread(this::deleteProducts).start();
        } else if (item.getItemId()==R.id.calculator) {
            Intent intent=new Intent(this,CaloriesCalculator.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void initDatabase(){
        appDataBase= AppDataBase.getInstance(this);
        foodDAO=appDataBase.foodDAO();
        new Thread(() -> {
            food=foodDAO.getByDay(LocalDate.now().toString(),true);
            FoodActivity.this.runOnUiThread(()->updateProgress(food));
        }).start();
        liveDateFood=foodDAO.getLiveData(LocalDate.now().toString());
        liveDateFood.observe(FoodActivity.this,observer);
    }
    private void updateProgress(FoodEntity food){
        caloriesBar.setCur((int) food.kcal);
        proteinBar.setCur((int) food.proteins);
        fatBar.setCur((int) food.fat);
        carbohydratesBar.setCur((int) food.carbohydrates);
    }
    private void deleteProducts(){
        ProductDAO productDAO= appDataBase.productDAO();
        List<ProductEntity> productEntities=productDAO.getByDay(LocalDate.now().toString());
        for(ProductEntity p: productEntities){
            productDAO.delete(p);
        }
    }
}
