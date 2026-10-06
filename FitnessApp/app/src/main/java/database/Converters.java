package database;

import androidx.room.TypeConverter;

import java.util.Arrays;
import java.util.List;

public class Converters {
    @TypeConverter
    public String fromList(List<String> list) {
        return String.join(";;", list);
    }

    @TypeConverter
    public List<String> toList(String data) {
        return Arrays.asList(data.split(";;"));
    }
}
