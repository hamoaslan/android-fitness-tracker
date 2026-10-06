package de.pbma.moa.fitnessapp.foodAPI;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface RetrofitAPI {
    @GET("api/v0/product/{barcode}.json")
    Call<FoodResponse> getProduct(@Path("barcode") String barcode);
}
