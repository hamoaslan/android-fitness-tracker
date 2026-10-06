package database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public abstract class ProductDAO {
    @Insert
    public abstract long insert(ProductEntity food);
    @Update
    public abstract void update(ProductEntity food);
    @Delete
    public abstract void delete(ProductEntity food);

    @Query("SELECT * FROM ProductEntity WHERE date=:date")
    public abstract List<ProductEntity> getByDay(String date);
}
