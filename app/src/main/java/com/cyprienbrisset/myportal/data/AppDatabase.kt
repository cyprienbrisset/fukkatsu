package com.cyprienbrisset.myportal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.cyprienbrisset.myportal.data.tile.TileDao
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileType

class Converters {
    @TypeConverter fun tileType(v: String): TileType = TileType.valueOf(v)
    @TypeConverter fun tileTypeToString(v: TileType): String = v.name
}

@Database(entities = [TileEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tileDao(): TileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "myportal.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
