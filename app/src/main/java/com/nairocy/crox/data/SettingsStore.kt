package com.nairocy.crox.data

import android.content.Context
import com.nairocy.crox.ChatMode
import com.nairocy.crox.Terminal
import com.nairocy.crox.ThemeMode

/** SharedPreferences（名 crox_settings）封装；启动读取、修改即写。 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var terminal: Terminal
        get() = enumOrDefault(prefs.getString(KEY_TERMINAL, null), Terminal.T1)
        set(value) = prefs.edit().putString(KEY_TERMINAL, value.name).apply()

    var mode: ChatMode
        get() = enumOrDefault(prefs.getString(KEY_MODE, null), ChatMode.Chat)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var thinking: Boolean
        get() = prefs.getBoolean(KEY_THINKING, true)
        set(value) = prefs.edit().putBoolean(KEY_THINKING, value).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var maxTokens: String
        get() = prefs.getString(KEY_MAX_TOKENS, "16384").orEmpty()
        set(value) = prefs.edit().putString(KEY_MAX_TOKENS, value).apply()

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var reasonBudget: String
        get() = prefs.getString(KEY_REASON_BUDGET, "256").orEmpty()
        set(value) = prefs.edit().putString(KEY_REASON_BUDGET, value).apply()

    var themeMode: ThemeMode
        get() = enumOrDefault(prefs.getString(KEY_THEME_MODE, null), ThemeMode.System)
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    fun load(): SettingsData = SettingsData(
        token = token,
        terminal = terminal,
        mode = mode,
        thinking = thinking,
        temperature = temperature,
        maxTokens = maxTokens,
        systemPrompt = systemPrompt,
        reasonBudget = reasonBudget,
        themeMode = themeMode,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        if (name == null) default else enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val PREFS_NAME = "crox_settings"
        const val KEY_TOKEN = "token"
        const val KEY_TERMINAL = "terminal"
        const val KEY_MODE = "mode"
        const val KEY_THINKING = "thinking"
        const val KEY_TEMPERATURE = "temp"
        const val KEY_MAX_TOKENS = "maxTokens"
        const val KEY_SYSTEM_PROMPT = "sys"
        const val KEY_REASON_BUDGET = "reasonBudget"
        const val KEY_THEME_MODE = "themeMode"
    }
}
