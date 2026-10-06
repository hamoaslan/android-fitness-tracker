package database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.time.LocalDate;

@Dao
public abstract class FoodDAO {
    @Insert
    public abstract long insert(FoodEntity food);

    @Update
    public abstract void update(FoodEntity food);
    @Delete
    public abstract void delete(FoodEntity food);

    @Query("SELECT * FROM FoodEntity WHERE date=:date")
    public abstract FoodEntity getByDay(String date);

    @Query("SELECT * FROM FoodEntity WHERE date=:date")
    public abstract LiveData<FoodEntity> getLiveData(String date);

    public FoodEntity getByDay(String date, boolean create){
        FoodEntity food=getByDay(date);
        if(food==null && create){
            food=new FoodEntity();
            food.date= LocalDate.now().toString();
            food.fat=0;
            food.proteins=0;
            food.carbohydrates=0;
            food.kcal=0;
            insert(food);
            food=getByDay(date);
        }
        return food;
    }
}
