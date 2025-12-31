package com.emailclient.di

import android.content.Context
import com.emailclient.data.local.CredentialManager
import com.emailclient.data.remote.oauth.OAuth2Service
import com.emailclient.data.remote.oauth.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for OAuth2 dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object OAuth2Module {

    @Provides
    @Singleton
    fun provideOAuth2Service(
        @ApplicationContext context: Context,
        credentialManager: CredentialManager
    ): OAuth2Service {
        return OAuth2Service(context, credentialManager)
    }

    @Provides
    @Singleton
    fun provideTokenManager(
        credentialManager: CredentialManager,
        oauth2Service: OAuth2Service
    ): TokenManager {
        return TokenManager(credentialManager, oauth2Service)
    }
}
