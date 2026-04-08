package com.psimandan.neuread.di

import android.app.Application
import android.content.Context
import com.psimandan.neuread.data.datasource.EBookDataSource
import com.psimandan.neuread.data.datasource.LibraryAssetDataSource
import com.psimandan.neuread.data.datasource.LibraryDiskDataSource
import com.psimandan.neuread.data.datasource.PrefsStore
import com.psimandan.neuread.data.datasource.PrefsStoreImpl
import com.psimandan.neuread.data.datasource.VoiceDataSource
import com.psimandan.neuread.data.repository.EBookRepository
import com.psimandan.neuread.data.repository.LibraryRepository
import com.psimandan.neuread.data.repository.LibraryRepositoryImpl
import com.psimandan.neuread.data.repository.PlayerStateRepository
import com.psimandan.neuread.data.repository.PlayerStateRepositoryImpl
import com.psimandan.neuread.data.repository.VoiceRepository
import com.psimandan.neuread.domain.usecase.BookmarkUseCase
import com.psimandan.neuread.domain.usecase.BookmarkUseCaseImpl
import com.psimandan.neuread.domain.usecase.PlayerUseCase
import com.psimandan.neuread.domain.usecase.PlayerUseCaseImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLibraryAssetDataSource(@ApplicationContext context: Context): LibraryAssetDataSource {
        return LibraryAssetDataSource(context)
    }

    @Provides
    @Singleton
    fun provideLibraryDiskDataSource(@ApplicationContext context: Context): LibraryDiskDataSource {
        return LibraryDiskDataSource(context)
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        diskDataSource: LibraryDiskDataSource,
        assetDataSource: LibraryAssetDataSource
    ): LibraryRepository {
        return LibraryRepositoryImpl(diskDataSource, assetDataSource)
    }

    @Provides
    @Singleton
    fun provideApplication(@ApplicationContext context: Context): Application {
        return context as Application
    }

    @Provides
    @Singleton
    fun provideEBookDataSource(@ApplicationContext context: Context): EBookDataSource {
        return EBookDataSource(context)
    }

    @Provides
    @Singleton
    fun provideEBookRepository(dataSource: EBookDataSource): EBookRepository {
        return EBookRepository(dataSource)
    }


    @Provides
    @Singleton
    fun provideVoiceDataSource(@ApplicationContext context: Context): VoiceDataSource {
        return VoiceDataSource(context)
    }

    @Provides
    @Singleton
    fun provideVoiceRepository(voiceDataSource: VoiceDataSource): VoiceRepository {
        return VoiceRepository(voiceDataSource)
    }

    @Singleton
    @Provides
    fun providePrefsStore(@ApplicationContext context: Context): PrefsStore =
        PrefsStoreImpl(context)

    @Provides
    @Singleton
    fun providePlayerStateRepository(): PlayerStateRepository {
        return PlayerStateRepositoryImpl()
    }

    @Provides
    @Singleton
    fun providePlayerUseCase(
        playerStateRepository: PlayerStateRepository,
        libraryRepository: LibraryRepository
    ): PlayerUseCase {
        return PlayerUseCaseImpl(playerStateRepository, libraryRepository)
    }

    @Provides
    @Singleton
    fun provideBookmarkUseCase(
        playerStateRepository: PlayerStateRepository
    ): BookmarkUseCase {
        return BookmarkUseCaseImpl(playerStateRepository)
    }
}
