package com.destinyai.astrology.di

import android.content.Context
import android.util.Log
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
import com.destinyai.astrology.data.remote.ErrorInterceptor
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.data.repository.CompatibilityRepository
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.data.repository.ProfileRepository
import com.destinyai.astrology.data.repository.impl.AuthRepositoryImpl
import com.destinyai.astrology.data.repository.impl.ChatRepositoryImpl
import com.destinyai.astrology.data.repository.impl.CompatibilityRepositoryImpl
import com.destinyai.astrology.data.repository.impl.HomeRepositoryImpl
import com.destinyai.astrology.data.repository.impl.ProfileRepositoryImpl
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(secure: SecureStorage): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC level keeps method+URL+status visible without leaking the
            // Authorization: Bearer <API_KEY> header to logcat (see security finding).
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                if (BuildConfig.API_KEY.isBlank()) {
                    Log.e(
                        "NetworkModule",
                        "BuildConfig.API_KEY is blank — skipping Authorization header. " +
                            "Rebuild with -PAPI_KEY=... or set DESTINY_API_KEY env var. " +
                            "All authenticated calls will return 401."
                    )
                } else {
                    builder.addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(ErrorInterceptor())
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            // Non-streaming Opus calls (POST /vedic/api/predict, /vedic/api/compatibility/analyze)
            // can hold the connection 3-5 min before the response lands. iOS uses
            // timeoutIntervalForResource=600s; we mirror the spec's "300s for
            // non-streaming, infinite for streaming" contract here.
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("streaming")
    fun provideStreamingOkHttpClient(secure: SecureStorage): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC level — never HEADERS — so the Authorization header is never logged.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                if (BuildConfig.API_KEY.isBlank()) {
                    Log.e(
                        "NetworkModule",
                        "BuildConfig.API_KEY is blank (streaming client) — skipping Authorization header."
                    )
                } else {
                    builder.addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(ErrorInterceptor())
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .pingInterval(30, TimeUnit.SECONDS)
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

    /**
     * Streaming Retrofit + AstroApiService — uses the @Named("streaming") OkHttpClient
     * with read/call timeouts disabled so long predictions don't get killed at 300s.
     * ChatRepositoryImpl + CompatibilityRepositoryImpl inject this for streamPredict /
     * streamCompatibilityAnalysis. All non-streaming endpoints continue to use the
     * default 300s-timeout client via the unqualified providers above.
     */
    @Provides
    @Singleton
    @Named("streaming")
    fun provideStreamingRetrofit(@Named("streaming") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @Named("streaming")
    fun provideStreamingAstroApiService(@Named("streaming") retrofit: Retrofit): AstroApiService =
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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_threads ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Adds astro_data_cache table mirroring iOS AstroDataCache + TodaysPredictionCache.
     * Composite primary key (kind, profile_id, birth_hash, year, month) so chart/dasha/
     * transits/today entries can coexist without colliding.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS astro_data_cache (" +
                    "kind TEXT NOT NULL, " +
                    "profile_id TEXT NOT NULL, " +
                    "birth_hash TEXT NOT NULL, " +
                    "year INTEGER NOT NULL, " +
                    "month INTEGER NOT NULL, " +
                    "owner_email TEXT NOT NULL, " +
                    "payload_json TEXT NOT NULL, " +
                    "saved_at_ms INTEGER NOT NULL, " +
                    "PRIMARY KEY(kind, profile_id, birth_hash, year, month))"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "destiny_db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

    @Provides
    fun provideAstroDataCacheDao(db: AppDatabase): com.destinyai.astrology.data.local.db.AstroDataCacheDao =
        db.astroDataCacheDao()
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

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
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
