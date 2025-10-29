package com.rapo.haloai.data.database.converters

import androidx.room.TypeConverter
import com.rapo.haloai.data.database.entities.ModelStatus

class ModelStatusConverter {
    
    @TypeConverter
    fun fromModelStatus(status: ModelStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toModelStatus(status: String): ModelStatus {
        return ModelStatus.valueOf(status)
    }
}
