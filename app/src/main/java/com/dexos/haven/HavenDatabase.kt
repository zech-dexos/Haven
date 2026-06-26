package com.dexos.haven

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class FloatArrayConverter {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): String =
        value.joinToString(",")

    @TypeConverter
    fun toFloatArray(value: String): FloatArray =
        if (value.isEmpty()) FloatArray(0)
        else value.split(",").map { it.toFloat() }.toFloatArray()
}

@Database(entities = [VoiceProfile::class], version = 1, exportSchema = false)
@TypeConverters(FloatArrayConverter::class)
abstract class HavenDatabase : RoomDatabase() {
    abstract fun voiceProfileDao(): VoiceProfileDao

    companion object {
        @Volatile private var INSTANCE: HavenDatabase? = null

        fun get(context: Context): HavenDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HavenDatabase::class.java,
                    "haven_db"
                ).build().also { INSTANCE = it }
            }
    }
}
