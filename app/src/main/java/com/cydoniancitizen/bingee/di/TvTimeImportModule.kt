package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.imports.tvtime.AndroidTvTimeZipGateway
import com.cydoniancitizen.bingee.data.imports.tvtime.DefaultTvTimeTmdbGateway
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeTmdbGateway
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeZipGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TvTimeImportModule {
    @Binds
    abstract fun bindTvTimeZipGateway(implementation: AndroidTvTimeZipGateway): TvTimeZipGateway

    @Binds
    abstract fun bindTvTimeTmdbGateway(implementation: DefaultTvTimeTmdbGateway): TvTimeTmdbGateway
}
