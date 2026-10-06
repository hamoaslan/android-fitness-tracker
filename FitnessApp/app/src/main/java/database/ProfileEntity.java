package database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import java.util.List;


@TypeConverters(Converters.class)
@Entity(tableName = "profile_table")
public class ProfileEntity {
    @PrimaryKey
    @NonNull
    public String name;

    public float accDownPushup;
    public float accUpPushup;
    public float accDownSquat;
    public float accUpSquat;
    public float accDownPullup;
    public float accUpPullup;

    public List<String> workoutLog;

    public ProfileEntity(String name,
                         float accDownPushup, float accUpPushup,
                         float accDownSquat, float accUpSquat,
                         float accDownPullup, float accUpPullup) {
        this.name = name;
        this.accDownPushup = accDownPushup;
        this.accUpPushup = accUpPushup;
        this.accDownSquat = accDownSquat;
        this.accUpSquat = accUpSquat;
        this.accDownPullup = accDownPullup;
        this.accUpPullup = accUpPullup;
        this.workoutLog = new java.util.ArrayList<>();
    }
}


