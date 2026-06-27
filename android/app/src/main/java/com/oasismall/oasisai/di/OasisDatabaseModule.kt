package com.oasismall.oasisai.di

import android.content.Context
import androidx.room.Room
import com.oasismall.oasisai.data.db.OasisDatabase
import com.oasismall.oasisai.data.db.OasisDatabaseMigrations
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.flavors.SubBarcodeRegistry
import com.oasismall.oasisai.domain.settings.BackupSecurityStore
import com.oasismall.oasisai.domain.settings.ImportantRayonsStore
import com.oasismall.oasisai.util.OasisLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OasisDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OasisDatabase =
        Room.databaseBuilder(context, OasisDatabase::class.java, "oasis_ai.db")
            .addMigrations(*OasisDatabaseMigrations.ALL)
            .fallbackToDestructiveMigration(false)
            .fallbackToDestructiveMigrationOnDowngrade(false)
            .build()
            .also { OasisLog.i(OasisLog.Domain.Database, "Room database opened (v${OasisDatabase.VERSION})") }

    @Provides
    @Singleton
    fun provideImportantRayonsStore(@ApplicationContext context: Context): ImportantRayonsStore =
        ImportantRayonsStore(context)

    @Provides
    @Singleton
    fun provideSubBarcodeRegistry(@ApplicationContext context: Context): SubBarcodeRegistry =
        SubBarcodeRegistry(context)

    @Provides
    @Singleton
    fun provideBackupSecurityStore(@ApplicationContext context: Context): BackupSecurityStore =
        BackupSecurityStore(context)

    @Provides
    @Singleton
    fun provideRepository(
        database: OasisDatabase,
        importantRayonsStore: ImportantRayonsStore,
        subBarcodeRegistry: SubBarcodeRegistry,
        @ApplicationContext context: Context,
    ): OasisRepository = OasisRepository(database, importantRayonsStore, subBarcodeRegistry, context.filesDir)

    @Provides
    @Singleton
    fun provideBackgroundTaskManager(): com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager =
        com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager()
}
