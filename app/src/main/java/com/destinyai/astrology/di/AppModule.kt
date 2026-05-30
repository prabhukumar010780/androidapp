package com.destinyai.astrology.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.local.db.AppDatabase
import com.destinyai.astrology.data.local.db.ChatMessageDao
import com.destinyai.astrology.data.local.db.ChatThreadDao
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.db.PartnerDao
import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.data.repository.CompatibilityRepository
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.data.repository.impl.AuthRepositoryImpl
import com.destinyai.astrology.data.repository.impl.ChatRepositoryImpl
import com.destinyai.astrology.data.repository.impl.CompatibilityRepositoryImpl
import com.destinyai.astrology.data.repository.impl.HomeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(secure: SecureStorage): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideAstroApiService(retrofit: Retrofit): AstroApiService =
        retrofit.create(AstroApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            // No-op placeholder; replace with real ALTER TABLE statements when schema bumps
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "destiny_db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideChatThreadDao(db: AppDatabase): ChatThreadDao = db.chatThreadDao()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun providePartnerDao(db: AppDatabase): PartnerDao = db.partnerDao()

    @Provides
    fun provideCompatibilityHistoryDao(db: AppDatabase): CompatibilityHistoryDao = db.compatibilityHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindCompatibilityRepository(impl: CompatibilityRepositoryImpl): CompatibilityRepository
}

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingManager(
        @ApplicationContext context: Context,
        api: AstroApiService,
        prefs: UserPreferences,
    ): BillingManager {
        // PurchasesUpdatedListener is wired after BillingManager is created
        // to avoid circular dependency — BillingManager exposes a factory helper.
        lateinit var manager: BillingManager
        val listener = PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                manager.processPurchases(purchases)
            } else {
                // delegate to the manager's own handler for other states
                manager.buildPurchasesUpdatedListener().onPurchasesUpdated(result, purchases)
            }
        }
        val billingClient = BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
        manager = BillingManager(billingClient, api, prefs)
        manager.startConnection()
        return manager
    }
}
