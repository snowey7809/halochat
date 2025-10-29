package com.rapo.haloai.data.database.dao

import androidx.room.*
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    
    @Query("SELECT * FROM installed_models ORDER BY downloadedAt DESC")
    fun getAllModels(): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM installed_models WHERE status = :status")
    fun getModelsByStatus(status: ModelStatus): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM installed_models WHERE id = :id")
    fun getModelById(id: String): Flow<ModelEntity?>
    
    @Query("SELECT * FROM installed_models WHERE status = 'READY' LIMIT 1")
    suspend fun getActiveModel(): ModelEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)
    
    @Update
    suspend fun updateModel(model: ModelEntity)
    
    @Delete
    suspend fun deleteModel(model: ModelEntity)
    
    @Query("DELETE FROM installed_models")
    suspend fun deleteAllModels()
}
