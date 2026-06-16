package co.terminal.radio

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AppConfig(
    val volume: Double = 0.8,
    val favorites: List<String> = emptyList()
) {
    companion object {
        private const val PREFS_NAME = "terminal_radio_prefs"
        private const val KEY_CONFIG = "app_config"

        fun load(context: Context): AppConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_CONFIG, null)
            return if (!json.isNullOrEmpty()) {
                Gson().fromJson(json, AppConfig::class.java)
            } else {
                AppConfig()
            }
        }

        fun save(context: Context, config: AppConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = Gson().toJson(config)
            prefs.edit().putString(KEY_CONFIG, json).apply()
        }
    }
}

fun AppConfig.toggleFavorite(name: String): AppConfig {
    val newFavorites = if (favorites.contains(name)) {
        favorites - name
    } else {
        favorites + name
    }
    return copy(favorites = newFavorites)
}

fun AppConfig.isFavorite(name: String): Boolean = favorites.contains(name)
