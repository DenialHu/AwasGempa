package com.example.awasgempa;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GempaDao {

    @Insert
    void insert(GempaHistory gempaHistory);

    @Delete
    void delete(GempaHistory gempaHistory);

    @Query("SELECT * FROM gempa_history ORDER BY saved_at DESC")
    List<GempaHistory> getAllHistory();

    @Query("DELETE FROM gempa_history")
    void deleteAll();

    @Query("SELECT * FROM gempa_history WHERE date_time = :dateTime LIMIT 1")
    GempaHistory getByDateTime(String dateTime);
}
