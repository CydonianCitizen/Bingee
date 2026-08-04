package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.importexport.AndroidBackupFileGateway
import com.cydoniancitizen.bingee.data.importexport.BackupFileGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupModule {
    @Binds
    abstract fun bindBackupFileGateway(implementation: AndroidBackupFileGateway): BackupFileGateway
}
