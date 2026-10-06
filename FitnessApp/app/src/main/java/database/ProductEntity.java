package database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ProductEntity")
public class ProductEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "date")
    public String date;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name="brand")
    public String brand;

    @ColumnInfo(name = "kcal")
    public float kcal;

    @ColumnInfo(name = "fat")
    public float fat;

    @ColumnInfo(name="carbohydrates")
    public float carbohydrates;

    @ColumnInfo(name = "proteins")
    public float proteins;

    @Override
    public String toString() {
        return name+" "+brand+" "+kcal+"kcal "+fat+"g "+carbohydrates+"g "+proteins+"g";
    }
}
