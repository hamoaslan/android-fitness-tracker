package database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "StepsEntity")
public class StepsEntity {
    @PrimaryKey()
    @ColumnInfo(name = "type")
    @NonNull
    public String type;

    @ColumnInfo(name = "steps")
    public int steps;

    @ColumnInfo(name = "time")
    public long time;
}
