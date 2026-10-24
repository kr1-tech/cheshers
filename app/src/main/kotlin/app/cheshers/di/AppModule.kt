package app.cheshers.di

import app.cheshers.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @Named("defaultOpenRouterApi")
    fun defaultOpenRouterApi(): String = BuildConfig.OPENROUTER_API

    @Provides @Singleton @Named("defaultBackendUrl")
    fun defaultBackendUrl(): String = BuildConfig.BACKEND_URL
}
