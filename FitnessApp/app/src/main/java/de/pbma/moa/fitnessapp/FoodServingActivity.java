package de.pbma.moa.fitnessapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.time.LocalDate;

import database.AppDataBase;
import database.FoodDAO;
import database.FoodEntity;
import database.ProductDAO;
import database.ProductEntity;
import de.pbma.moa.fitnessapp.foodAPI.Product;

public class FoodServingActivity extends AppCompatActivity {
    private static final String TAG="FoodServingActivity";
    private TextView nutriments;
    private Button cancel;
    private Button enter;
    private EditText serving_size;
    private Product product;
    private Product servProduct;
    private AppDataBase appDataBase;
    private FoodDAO foodDAO;
    private ProductDAO productDAO;
    private FoodEntity food;
    private ProductEntity productEntity;

    private TextWatcher textWatcher=new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(nutriments!=null && serving_size!=null){
                int portion=0;
                if(!s.toString().isEmpty()){
                    portion=Integer.parseInt(s.toString());
                }
                float multi= (float) portion /100;
                servProduct=product.multi(multi);
                FoodServingActivity.this.runOnUiThread(()->updateText(servProduct));
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };
    private void onClickCancel(View view){
        finish();
    }
    private void onClickEnter(View view){
        Log.v(TAG,"onClickEnter");
        if(food!=null) {
            food.kcal+=servProduct.getNutriments().getEnergy_kcal();
            productEntity.kcal=food.kcal;
            food.fat+=servProduct.getNutriments().getFat();
            productEntity.fat=food.fat;
            food.carbohydrates+=servProduct.getNutriments().getCarbohydrates();
            productEntity.carbohydrates=food.carbohydrates;
            food.proteins+=servProduct.getNutriments().getProteins();
            productEntity.proteins=food.proteins;
            new Thread(()->foodDAO.update(food)).start();
            new Thread(()->productDAO.insert(productEntity)).start();
            finish();
        }
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.food_serving_layout);
        Bundle bundle=getIntent().getExtras();
        assert bundle != null;
        String json=bundle.getString("INFO");
        Gson gson=new Gson();
        product=gson.fromJson(json,Product.class);
        productEntity=toEntity(product);
        servProduct=product;
        //Nutriments
        nutriments=findViewById(R.id.nutriments);
        //Buttons
        cancel=findViewById(R.id.btn_cancel);
        enter=findViewById(R.id.btn_enter);
        cancel.setOnClickListener(this::onClickCancel);
        enter.setOnClickListener(this::onClickEnter);
        //Edittext
        serving_size=findViewById(R.id.size_edit);
        serving_size.setText("100");
        serving_size.addTextChangedListener(textWatcher);
        updateText(product);
        //Database
        initDatabase();
    }
    private void updateText(Product p){
        nutriments.setText(p.toString());
    }
    private void initDatabase(){
        appDataBase=AppDataBase.getInstance(this);
        foodDAO=appDataBase.foodDAO();
        productDAO=appDataBase.productDAO();
        new Thread(() -> food=foodDAO.getByDay(LocalDate.now().toString(),true)).start();
    }
    private ProductEntity toEntity(Product product){
        ProductEntity productEntity=new ProductEntity();
        productEntity.name=product.getProduct_name();
        productEntity.brand=product.getBrands();
        productEntity.date=LocalDate.now().toString();
        productEntity.kcal=product.getNutriments().getEnergy_kcal();
        productEntity.fat=product.getNutriments().getFat();
        productEntity.proteins=product.getNutriments().getProteins();
        productEntity.carbohydrates=product.getNutriments().getCarbohydrates();
        return productEntity;
    }
}
