package database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;


@Entity(tableName = "FoodEntity")
public class FoodEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "date")
    public String date;

    @ColumnInfo(name = "kcal")
    public float kcal;

    @ColumnInfo(name = "fat")
    public float fat;

    @ColumnInfo(name="carbohydrates")
    public float carbohydrates;

    @ColumnInfo(name = "proteins")
    public float proteins;
}
