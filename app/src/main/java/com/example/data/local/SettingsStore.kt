package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.EditMode
import com.example.data.model.ExportFormat
import com.example.data.model.ExportOption
import com.example.data.model.ExportSizePreset
import com.example.data.model.QualityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cutout_pro_settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val lastUsedMode: EditMode = EditMode.BG_REMOVE,
    val defaultMode: EditMode = EditMode.BG_REMOVE,
    val segmentationQuality: QualityMode = QualityMode.HIGH,
    val fillQuality: QualityMode = QualityMode.HIGH,
    val defaultBgKey: String = "TRANSPARENT",
    val amoledTheme: Boolean = false,
    val autoSave: Boolean = true,
    val videoBetaEnabled: Boolean = false,
    val defaultExportOption: ExportOption = ExportOption.BG_REMOVE,
    val defaultExportFormat: ExportFormat = ExportFormat.PNG,
    val defaultExportQuality: Int = 92,
    val defaultExportSize: ExportSizePreset = ExportSizePreset.ORIGINAL,
    val defaultSaveFolderUri: String = "",
    val askBeforeSaving: Boolean = true,
    val fileNamePattern: String = "cutout_{date}_{mode}"
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_USED_MODE = stringPreferencesKey("last_used_mode")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val SEG_QUALITY = stringPreferencesKey("seg_quality")
        val FILL_QUALITY = stringPreferencesKey("fill_quality")
        val DEFAULT_BG_KEY = stringPreferencesKey("default_bg_key")
        val AMOLED_THEME = booleanPreferencesKey("amoled_theme")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val VIDEO_BETA = booleanPreferencesKey("video_beta")
        val EXP_OPTION = stringPreferencesKey("exp_option")
        val EXP_FORMAT = stringPreferencesKey("exp_format")
        val EXP_QUALITY = intPreferencesKey("exp_quality")
        val EXP_SIZE = stringPreferencesKey("exp_size")
        val EXP_FOLDER_URI = stringPreferencesKey("exp_folder_uri")
        val ASK_BEFORE_SAVE = booleanPreferencesKey("ask_before_save")
        val FILENAME_PATTERN = stringPreferencesKey("filename_pattern")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            lastUsedMode = parseEnum(prefs[Keys.LAST_USED_MODE], EditMode.BG_REMOVE),
            defaultMode = parseEnum(prefs[Keys.DEFAULT_MODE], EditMode.BG_REMOVE),
            segmentationQuality = parseEnum(prefs[Keys.SEG_QUALITY], QualityMode.HIGH),
            fillQuality = parseEnum(prefs[Keys.FILL_QUALITY], QualityMode.HIGH),
            defaultBgKey = prefs[Keys.DEFAULT_BG_KEY] ?: "TRANSPARENT",
            amoledTheme = prefs[Keys.AMOLED_THEME] ?: false,
            autoSave = prefs[Keys.AUTO_SAVE] ?: true,
            videoBetaEnabled = prefs[Keys.VIDEO_BETA] ?: false,
            defaultExportOption = parseEnum(prefs[Keys.EXP_OPTION], ExportOption.BG_REMOVE),
            defaultExportFormat = parseEnum(prefs[Keys.EXP_FORMAT], ExportFormat.PNG),
            defaultExportQuality = prefs[Keys.EXP_QUALITY] ?: 92,
            defaultExportSize = parseEnum(prefs[Keys.EXP_SIZE], ExportSizePreset.ORIGINAL),
            defaultSaveFolderUri = prefs[Keys.EXP_FOLDER_URI] ?: "",
            askBeforeSaving = prefs[Keys.ASK_BEFORE_SAVE] ?: true,
            fileNamePattern = prefs[Keys.FILENAME_PATTERN] ?: "cutout_{date}_{mode}"
        )
    }

    suspend fun setOnboardingDone(done: Boolean) = update { it[Keys.ONBOARDING_DONE] = done }
    suspend fun setLastUsedMode(mode: EditMode) = update { it[Keys.LAST_USED_MODE] = mode.name }
    suspend fun setDefaultMode(mode: EditMode) = update { it[Keys.DEFAULT_MODE] = mode.name }
    suspend fun setSegmentationQuality(q: QualityMode) = update { it[Keys.SEG_QUALITY] = q.name }
    suspend fun setFillQuality(q: QualityMode) = update { it[Keys.FILL_QUALITY] = q.name }
    suspend fun setDefaultBgKey(key: String) = update { it[Keys.DEFAULT_BG_KEY] = key }
    suspend fun setAmoledTheme(enabled: Boolean) = update { it[Keys.AMOLED_THEME] = enabled }
    suspend fun setAutoSave(enabled: Boolean) = update { it[Keys.AUTO_SAVE] = enabled }
    suspend fun setVideoBetaEnabled(enabled: Boolean) = update { it[Keys.VIDEO_BETA] = enabled }
    suspend fun setDefaultExportOption(opt: ExportOption) = update { it[Keys.EXP_OPTION] = opt.name }
    suspend fun setDefaultExportFormat(fmt: ExportFormat) = update { it[Keys.EXP_FORMAT] = fmt.name }
    suspend fun setDefaultExportQuality(q: Int) = update { it[Keys.EXP_QUALITY] = q }
    suspend fun setDefaultExportSize(size: ExportSizePreset) = update { it[Keys.EXP_SIZE] = size.name }
    suspend fun setDefaultSaveFolderUri(uri: String) = update { it[Keys.EXP_FOLDER_URI] = uri }
    suspend fun setAskBeforeSaving(ask: Boolean) = update { it[Keys.ASK_BEFORE_SAVE] = ask }
    suspend fun setFileNamePattern(pattern: String) = update { it[Keys.FILENAME_PATTERN] = pattern }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T {
        if (raw == null) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
