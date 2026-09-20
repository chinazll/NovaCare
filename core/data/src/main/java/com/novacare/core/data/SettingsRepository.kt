package com.novacare.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novacare.core.model.CloudModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("novacare_settings")

/** 主题模式：跟随系统 / 强制浅色 / 强制深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 设置仓库（DataStore）
 *
 * 隐私红线落地处：
 * - [advancedMode]：高级模式（Shizuku / 组件管理）默认关闭
 * - [cloudAiEnabled]：云端 AI 默认**关闭** —— 未开启时 App 不发任何网络请求
 * - [cloudModel]：云端模型，中国区默认 MiniMax（已备案，数据不出境）
 * - [themeMode]：主题模式，默认跟随系统
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Settings(
        val advancedMode: Boolean = false,
        val cloudAiEnabled: Boolean = false,
        val cloudModel: CloudModel = CloudModel.MINIMAX,
        val cloudApiKey: String = "",
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val lastCleanEpochMs: Long? = null,
        /** 引导流程是否已走完 —— 未走完时首屏显示 Onboarding */
        val onboardingCompleted: Boolean = false,
    )

    // DataStore 读失败（文件损坏 / IO 错误）时 `data` 流会以异常终止，且**不会恢复**，
    // 于是所有 collect 它的页面永久收不到设置更新。这里吞掉错误并退化为默认值。
    val settings: Flow<Settings> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        Settings(
            advancedMode = prefs[KEY_ADVANCED] ?: false,
            cloudAiEnabled = prefs[KEY_CLOUD_AI] ?: false,
            cloudModel = runCatching {
                CloudModel.valueOf(prefs[KEY_CLOUD_MODEL] ?: CloudModel.MINIMAX.name)
            }.getOrDefault(CloudModel.MINIMAX),
            cloudApiKey = prefs[KEY_CLOUD_KEY] ?: "",
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            lastCleanEpochMs = prefs[KEY_LAST_CLEAN],
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
        )
    }

    suspend fun setAdvancedMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_ADVANCED] = enabled }
    }

    suspend fun setCloudAi(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_CLOUD_AI] = enabled }
    }

    suspend fun setCloudModel(model: CloudModel) {
        context.settingsDataStore.edit { it[KEY_CLOUD_MODEL] = model.name }
    }

    suspend fun setCloudApiKey(key: String) {
        context.settingsDataStore.edit { it[KEY_CLOUD_KEY] = key }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setLastClean(nowMs: Long) {
        context.settingsDataStore.edit { it[KEY_LAST_CLEAN] = nowMs }
    }

    /** 标记引导流程已完成（完成或跳过都调用它 —— 之后不再显示 Onboarding） */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[KEY_ONBOARDING] = completed }
    }

    private companion object {
        val KEY_ADVANCED = booleanPreferencesKey("advanced_mode")
        val KEY_CLOUD_AI = booleanPreferencesKey("cloud_ai_enabled")
        val KEY_CLOUD_MODEL = stringPreferencesKey("cloud_model")
        val KEY_CLOUD_KEY = stringPreferencesKey("cloud_api_key")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LAST_CLEAN = androidx.datastore.preferences.core.longPreferencesKey("last_clean_epoch_ms")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
    }
}
