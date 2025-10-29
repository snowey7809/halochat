package com.rapo.haloai.di

import android.content.Context
import androidx.room.Room
import com.rapo.haloai.data.database.HaloDatabase
import com.rapo.haloai.data.database.dao.ChatDao
import com.rapo.haloai.data.database.dao.ModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HaloDatabase {
        return Room.databaseBuilder(
            context,
            HaloDatabase::class.java,
            "halo_database"
        ).build()
    }
    
    @Provides
    fun provideChatDao(database: HaloDatabase): ChatDao {
        return database.chatDao()
    }
    
    @Provides
    fun provideModelDao(database: HaloDatabase): ModelDao {
        return database.modelDao()
    }
}
