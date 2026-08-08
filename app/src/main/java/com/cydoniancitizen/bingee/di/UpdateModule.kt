package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.update.DefaultAppUpdateRepository
import com.cydoniancitizen.bingee.data.update.GitHubReleaseService
import com.cydoniancitizen.bingee.domain.repository.AppUpdateRepository
import dagger.Binds
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
internal abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: DefaultAppUpdateRepository): AppUpdateRepository

    companion object {
        @Provides
        @Singleton
        fun provideGitHubReleaseService(client: OkHttpClient): GitHubReleaseService = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubReleaseService::class.java)
    }
}
