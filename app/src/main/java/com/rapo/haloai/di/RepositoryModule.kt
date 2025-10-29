package com.rapo.haloai.di

import com.rapo.haloai.data.repository.ChatRepository
import com.rapo.haloai.data.repository.ModelRepository
import com.rapo.haloai.data.database.dao.ChatDao
import com.rapo.haloai.data.database.dao.ModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao): ChatRepository {
        return ChatRepository(chatDao)
    }
    
    @Provides
    @Singleton
    fun provideModelRepository(modelDao: ModelDao): ModelRepository {
        return ModelRepository(modelDao)
    }
}
