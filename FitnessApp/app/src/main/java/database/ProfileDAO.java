package database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import androidx.room.OnConflictStrategy;
import androidx.room.Update;


import java.util.List;

@Dao
public interface ProfileDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(database.ProfileEntity profile);

    @Query("SELECT * FROM profile_table")
    List<database.ProfileEntity>  getAll();

    @Query("SELECT * FROM profile_table WHERE name = :name LIMIT 1")
    database.ProfileEntity getByName(String name);

    @Update
    void update(ProfileEntity profile);


    @Delete
    void delete(database.ProfileEntity profile);
}
