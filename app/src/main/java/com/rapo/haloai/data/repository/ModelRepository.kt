package com.rapo.haloai.data.repository

import com.rapo.haloai.data.database.dao.ModelDao
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao
) {
    
    fun getAllModels(): Flow<List<ModelEntity>> {
        return modelDao.getAllModels()
    }
    
    fun getModelsByStatus(status: ModelStatus): Flow<List<ModelEntity>> {
        return modelDao.getModelsByStatus(status)
    }
    
    fun getModelById(id: String): Flow<ModelEntity?> {
        return modelDao.getModelById(id)
    }
    
    suspend fun getActiveModel(): ModelEntity? {
        return modelDao.getActiveModel()
    }
    
    suspend fun insertModel(model: ModelEntity) {
        modelDao.insertModel(model)
    }
    
    suspend fun updateModel(model: ModelEntity) {
        modelDao.updateModel(model)
    }
    
    suspend fun deleteModel(model: ModelEntity) {
        modelDao.deleteModel(model)
    }
    
    suspend fun deleteAllModels() {
        modelDao.deleteAllModels()
    }
}
