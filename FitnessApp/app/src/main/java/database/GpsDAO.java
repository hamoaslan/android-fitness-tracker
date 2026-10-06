package database;


import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public abstract class GpsDAO {
    @Insert
    public abstract void insert(GpsEntity point);
    @Update
    public abstract void update(GpsEntity point);
    @Delete
    public abstract void delete(GpsEntity point);

    @Query("SElECT * From GpsEntity WHERE routename = :route ORDER BY time ASC")
    public abstract List<GpsEntity> getRoute(String route);
    @Query("SELECT DISTINCT routename FROM GpsEntity")
    public abstract List<String> getRoutes();
    @Query("SELECT COUNT(*) FROM GpsEntity WHERE routename = :route")
    public abstract int checkRoute(String route);


}
