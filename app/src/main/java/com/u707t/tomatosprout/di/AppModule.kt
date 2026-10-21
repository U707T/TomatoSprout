package com.u707t.tomatosprout.di

import android.content.Context
import androidx.room.Room
import com.u707t.tomatosprout.data.db.AppDatabase
import com.u707t.tomatosprout.data.repo.BadgeRepositoryImpl
import com.u707t.tomatosprout.data.repo.CheckInRepositoryImpl
import com.u707t.tomatosprout.data.repo.FocusRepositoryImpl
import com.u707t.tomatosprout.data.repo.ReminderRepositoryImpl
import com.u707t.tomatosprout.data.repo.TaskRepositoryImpl
import com.u707t.tomatosprout.data.repo.WishRepositoryImpl
import com.u707t.tomatosprout.data.settings.SettingsRepositoryImpl
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.BadgeRefresher
import com.u707t.tomatosprout.domain.BadgeRepository
import com.u707t.tomatosprout.domain.BadgeUseCase
import com.u707t.tomatosprout.domain.CheckInRepository
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.ReminderRepository
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.SubTaskRepository
import com.u707t.tomatosprout.domain.SystemAppClock
import com.u707t.tomatosprout.domain.TaskRepository
import com.u707t.tomatosprout.domain.WishRepository
import dagger.Binds
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
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun subTaskDao(db: AppDatabase) = db.subTaskDao()
    @Provides fun habitDao(db: AppDatabase) = db.habitRuleDao()
    @Provides fun goalDao(db: AppDatabase) = db.goalRuleDao()
    @Provides fun checkInDao(db: AppDatabase) = db.checkInDao()
    @Provides fun focusDao(db: AppDatabase) = db.focusDao()
    @Provides fun badgeDao(db: AppDatabase) = db.badgeDao()
    @Provides fun reminderDao(db: AppDatabase) = db.reminderDao()
    @Provides fun wishDao(db: AppDatabase) = db.wishDao()
    @Provides fun syncDao(db: AppDatabase) = db.syncDao()

    @Provides
    @Singleton
    fun clock(): AppClock = SystemAppClock
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindModule {
    @Binds @Singleton abstract fun taskRepo(impl: TaskRepositoryImpl): TaskRepository
    @Binds @Singleton abstract fun subTaskRepo(impl: TaskRepositoryImpl): SubTaskRepository
    @Binds @Singleton abstract fun checkInRepo(impl: CheckInRepositoryImpl): CheckInRepository
    @Binds @Singleton abstract fun focusRepo(impl: FocusRepositoryImpl): FocusRepository
    @Binds @Singleton abstract fun badgeRepo(impl: BadgeRepositoryImpl): BadgeRepository
    @Binds @Singleton abstract fun badgeRefresher(impl: BadgeUseCase): BadgeRefresher
    @Binds @Singleton abstract fun reminderRepo(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds @Singleton abstract fun wishRepo(impl: WishRepositoryImpl): WishRepository
    @Binds @Singleton abstract fun settingsRepo(impl: SettingsRepositoryImpl): SettingsRepository
}
