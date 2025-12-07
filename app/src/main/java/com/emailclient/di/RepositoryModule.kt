package com.emailclient.di

import com.emailclient.data.repository.AccountRepositoryImpl
import com.emailclient.data.repository.EmailRepositoryImpl
import com.emailclient.data.repository.FolderRepositoryImpl
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.domain.repository.FolderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing repository implementations
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        accountRepositoryImpl: AccountRepositoryImpl
    ): AccountRepository

    @Binds
    @Singleton
    abstract fun bindEmailRepository(
        emailRepositoryImpl: EmailRepositoryImpl
    ): EmailRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        folderRepositoryImpl: FolderRepositoryImpl
    ): FolderRepository
}
