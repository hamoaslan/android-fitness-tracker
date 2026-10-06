package de.pbma.moa.fitnessapp.foodAPI;

import com.google.gson.annotations.SerializedName;

public class Nutriments {

    @SerializedName("energy-kcal_100g")
    private float energyKcal;

    @SerializedName("fat_100g")
    private float fat;

    @SerializedName("carbohydrates_100g")
    private float carbohydrates;

    @SerializedName("proteins_100g")
    private float proteins;

    public float getEnergy_kcal() {
        return energyKcal;
    }

    public float getFat() {
        return fat;
    }

    public float getCarbohydrates() {
        return carbohydrates;
    }

    public float getProteins() {
        return proteins;
    }

    @Override
    public String toString() {
        return "Kcal= " + energyKcal +'\n' +
                "fat= " + fat +"g"+'\n' +
                "Carbs= " + carbohydrates+ "g" +'\n' +
                "proteins= " + proteins +"g";
    }
    public Nutriments multi(float f){
        Nutriments n=new Nutriments();
        n.energyKcal=Math.round(this.energyKcal*f*10f)/10f;
        n.proteins=Math.round(this.proteins*f*10f)/10f;
        n.fat=Math.round(this.fat*f*10f)/10f;
        n.carbohydrates=Math.round(this.carbohydrates*f*10f)/10f;
        return n;
    }
}
