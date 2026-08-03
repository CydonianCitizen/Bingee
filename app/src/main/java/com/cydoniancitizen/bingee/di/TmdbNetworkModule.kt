package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.tmdb.auth.TmdbAuthenticationService
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsService
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbSearchService
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
internal object TmdbNetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideTmdbAuthenticationService(retrofit: Retrofit): TmdbAuthenticationService =
        retrofit.create(TmdbAuthenticationService::class.java)

    @Provides
    @Singleton
    fun provideTmdbSearchService(retrofit: Retrofit): TmdbSearchService = retrofit.create(TmdbSearchService::class.java)

    @Provides
    @Singleton
    fun provideTmdbDetailsService(retrofit: Retrofit): TmdbDetailsService =
        retrofit.create(TmdbDetailsService::class.java)

    @Provides
    @Singleton
    fun provideTmdbSeasonService(retrofit: Retrofit): TmdbSeasonService = retrofit.create(TmdbSeasonService::class.java)
}
