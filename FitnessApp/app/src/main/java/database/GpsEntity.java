package database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "GpsEntity")
public class GpsEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;
    @ColumnInfo(name = "routename")
    public String routename;
    @ColumnInfo(name = "longitude")
    public double longitude;
    @ColumnInfo(name = "latitude")
    public double latitude;
    @ColumnInfo(name = "speed")
    public float speed;
    @ColumnInfo(name = "time")
    public long time;
}
