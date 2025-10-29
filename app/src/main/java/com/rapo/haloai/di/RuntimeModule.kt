package com.rapo.haloai.di

import android.content.Context
import com.rapo.haloai.data.model.GGUFModelRuntime
import com.rapo.haloai.data.model.HardwareAccelerationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {
    
    @Provides
    @Singleton
    fun provideGGUFRuntime(): GGUFModelRuntime {
        return GGUFModelRuntime()
    }
    
    @Provides
    @Singleton
    fun provideHardwareAccelerationManager(
        @ApplicationContext context: Context
    ): HardwareAccelerationManager {
        return HardwareAccelerationManager(context)
    }
}
