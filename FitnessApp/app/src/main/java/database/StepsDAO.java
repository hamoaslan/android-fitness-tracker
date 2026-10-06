package database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public abstract class StepsDAO {
    @Insert
    public abstract long insert(StepsEntity steps);
    @Update
    public abstract void update(StepsEntity steps);
    @Delete
    abstract void delete(StepsEntity steps);

    @Query("SELECT * FROM StepsEntity WHERE type=:type")
    public abstract StepsEntity getByType(String type);

    @Query("SELECT * FROM StepsEntity WHERE type=:type")
    public abstract LiveData<StepsEntity> getLiveData(String type);

    public StepsEntity getByType(String type,boolean create){
        if(create){
            if(getByType(type)==null){
                StepsEntity steps=new StepsEntity();
                steps.steps=0;
                steps.type=type;
                steps.time=System.nanoTime();
                insert(steps);
            }
        }
        return getByType(type);
    }
}
