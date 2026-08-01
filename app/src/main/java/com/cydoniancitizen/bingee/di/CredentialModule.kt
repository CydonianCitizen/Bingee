package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.credential.AndroidKeystoreTmdbCredentialCipher
import com.cydoniancitizen.bingee.data.credential.DefaultTmdbCredentialRepository
import com.cydoniancitizen.bingee.data.credential.EncryptedTmdbCredentialStore
import com.cydoniancitizen.bingee.data.credential.NoBackupTmdbCredentialFile
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialCipher
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialFile
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.settings.DataStoreFirstRunPreferences
import com.cydoniancitizen.bingee.data.settings.FirstRunPreferences
import com.cydoniancitizen.bingee.data.tmdb.auth.TmdbCredentialRemoteValidator
import com.cydoniancitizen.bingee.data.tmdb.auth.TmdbCredentialValidationClient
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CredentialModule {
    @Binds
    @Singleton
    abstract fun bindCredentialStore(implementation: EncryptedTmdbCredentialStore): TmdbCredentialStore

    @Binds
    @Singleton
    abstract fun bindCredentialCipher(implementation: AndroidKeystoreTmdbCredentialCipher): TmdbCredentialCipher

    @Binds
    @Singleton
    abstract fun bindCredentialFile(implementation: NoBackupTmdbCredentialFile): TmdbCredentialFile

    @Binds
    @Singleton
    abstract fun bindCredentialRemoteValidator(
        implementation: TmdbCredentialValidationClient
    ): TmdbCredentialRemoteValidator

    @Binds
    abstract fun bindCredentialRepository(implementation: DefaultTmdbCredentialRepository): TmdbCredentialRepository

    @Binds
    abstract fun bindFirstRunPreferences(implementation: DataStoreFirstRunPreferences): FirstRunPreferences
}
