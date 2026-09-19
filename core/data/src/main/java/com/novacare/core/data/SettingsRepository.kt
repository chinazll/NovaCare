package com.novacare.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novacare.core.model.CloudModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("novacare_settings")

/**
 * 设置仓库（DataStore）
 *
 * 隐私红线落地处：
 * - [advancedMode]：高级模式（Shizuku / 组件管理）默认关闭
 * - [cloudAiEnabled]：云端 AI 默认**关闭** —— 未开启时 App 不发任何网络请求
 * - [cloudModel]：云端模型，中国区默认 MiniMax（已备案，数据不出境）
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
        val lastCleanEpochMs: Long? = null,
    )

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            advancedMode = prefs[KEY_ADVANCED] ?: false,
            cloudAiEnabled = prefs[KEY_CLOUD_AI] ?: false,
            cloudModel = runCatching {
                CloudModel.valueOf(prefs[KEY_CLOUD_MODEL] ?: CloudModel.MINIMAX.name)
            }.getOrDefault(CloudModel.MINIMAX),
            cloudApiKey = prefs[KEY_CLOUD_KEY] ?: "",
            lastCleanEpochMs = prefs[KEY_LAST_CLEAN],
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

    suspend fun setLastClean(nowMs: Long) {
        context.settingsDataStore.edit { it[KEY_LAST_CLEAN] = nowMs }
    }

    private companion object {
        val KEY_ADVANCED = booleanPreferencesKey("advanced_mode")
        val KEY_CLOUD_AI = booleanPreferencesKey("cloud_ai_enabled")
        val KEY_CLOUD_MODEL = stringPreferencesKey("cloud_model")
        val KEY_CLOUD_KEY = stringPreferencesKey("cloud_api_key")
        val KEY_LAST_CLEAN = androidx.datastore.preferences.core.longPreferencesKey("last_clean_epoch_ms")
    }
}
