package de.pbma.moa.fitnessapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import database.AppDataBase;
import database.FoodDAO;
import database.FoodEntity;
import database.ProductDAO;
import database.ProductEntity;

public class FoodListActivity extends AppCompatActivity {
    private static final String TAG="FoodListActivity";
    private LinearLayout linearLayout;
    private AppDataBase appDataBase;
    private ProductDAO productDAO;
    private FoodDAO foodDAO;
    private Map<TextView,ProductEntity> productEntityMap=new HashMap<>();
    private ViewGroup.LayoutParams layoutParams=new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG,"onCreate");
        setContentView(R.layout.food_list_layout);
        Button cancel=findViewById(R.id.btn_cancel_food);
        linearLayout=findViewById(R.id.ll_scrollView_food);
        cancel.setOnClickListener((view)->finish());
        appDataBase=AppDataBase.getInstance(this);
        productDAO=appDataBase.productDAO();
        foodDAO=appDataBase.foodDAO();
        new Thread(this::displayProducts).start();
    }

    private void displayProducts(){
        List<ProductEntity> productEntities=productDAO.getByDay(LocalDate.now().toString());
        Log.v(TAG,productEntities.toString());
        for(ProductEntity p: productEntities){
            TextView textView=new TextView(FoodListActivity.this);
            textView.setLayoutParams(layoutParams);
            textView.setText(p.toString());
            textView.setClickable(true);
            textView.setOnClickListener(this::onClickTextView);
            productEntityMap.put(textView,p);
            FoodListActivity.this.runOnUiThread(()->linearLayout.addView(textView));
        }
    }
    private void onClickTextView(View view){
        ProductEntity p=productEntityMap.get(view);
        FoodListActivity.this.runOnUiThread(()->linearLayout.removeView(view));
        new Thread(()->updateFood(p)).start();
        productEntityMap.remove(view);
    }
    private void updateFood(ProductEntity p){
        FoodEntity food=foodDAO.getByDay(LocalDate.now().toString());
        food.kcal-=p.kcal;
        food.fat-=p.fat;
        food.carbohydrates-=p.carbohydrates;
        food.proteins-=p.proteins;
        foodDAO.update(food);
        productDAO.delete(p);
    }
}
