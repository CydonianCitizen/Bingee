package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.jikan.DefaultJikanDelay
import com.cydoniancitizen.bingee.data.jikan.JikanDelay
import com.cydoniancitizen.bingee.data.jikan.search.JikanSearchService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
internal object JikanNetworkModule {
    @Provides
    @Singleton
    fun provideJikanDelay(): JikanDelay = DefaultJikanDelay

    @Provides
    @Singleton
    @Named("jikan")
    fun provideJikanOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    @Named("jikan")
    fun provideJikanRetrofit(@Named("jikan") client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.jikan.moe/v4/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideJikanSearchService(@Named("jikan") retrofit: Retrofit): JikanSearchService =
        retrofit.create(JikanSearchService::class.java)
}
