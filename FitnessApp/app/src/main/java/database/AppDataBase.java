package database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {StepsEntity.class, ProfileEntity.class, GpsEntity.class, FoodEntity.class, ProductEntity.class},version = 4,exportSchema = false)
public abstract class AppDataBase extends RoomDatabase {
    public abstract ProfileDAO profileDAO();
    public abstract StepsDAO stepsDAO();
    public abstract GpsDAO gpsDAO();
    public abstract FoodDAO foodDAO();
    public abstract ProductDAO productDAO();
    private static AppDataBase INSTANCE=null;
    private static AppDataBase createInstance(Context context){
        return Room.databaseBuilder(context,AppDataBase.class,"stepsdb")
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .build();
    }
    public static synchronized AppDataBase getInstance(Context context){
        if(INSTANCE==null){
            INSTANCE=createInstance(context);
        }
        return INSTANCE;
    }
}
