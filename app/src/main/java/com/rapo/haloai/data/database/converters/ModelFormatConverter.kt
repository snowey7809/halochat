package com.rapo.haloai.data.database.converters

import androidx.room.TypeConverter
import com.rapo.haloai.data.database.entities.ModelFormat

class ModelFormatConverter {
    
    @TypeConverter
    fun fromModelFormat(format: ModelFormat): String {
        return format.name
    }
    
    @TypeConverter
    fun toModelFormat(format: String): ModelFormat {
        return ModelFormat.valueOf(format)
    }
}
