package com.thewizrd.shared_resources.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.thewizrd.shared_resources.locationdata.LocationData;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.weatherdata.Favorites;

@Database(entities = {LocationData.class, Favorites.class}, version = Settings.CURRENT_DBVERSION)
@TypeConverters({LocationDBConverters.class})
public abstract class LocationsDatabase extends RoomDatabase {
    public abstract LocationsDAO locationsDAO();
}
