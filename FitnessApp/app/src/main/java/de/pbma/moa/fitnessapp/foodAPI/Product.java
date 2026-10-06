package de.pbma.moa.fitnessapp.foodAPI;

import java.text.ParseException;

public class Product {
    private String product_name;
    private String brands;
    private Nutriments nutriments;

    public String getProduct_name() {
        return product_name;
    }

    public String getBrands() {
        return brands;
    }

    public Nutriments getNutriments() {
        return nutriments;
    }

    @Override
    public String toString() {
        return
                "Name= " + product_name + '\n' +
                "Brand= " + brands + '\n' +
                nutriments.toString();
    }
    public Product multi(float mul){
        Product p=new Product();
        p.product_name=this.product_name;
        p.brands=this.brands;
        p.nutriments=this.nutriments.multi(mul);
        return p;
    }
}
