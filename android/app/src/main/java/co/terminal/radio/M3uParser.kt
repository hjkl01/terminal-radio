package co.terminal.radio

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Station(val name: String, val url: String)

object M3uParser {
    private val gson = Gson()

    fun parse(rawContent: String): List<Station> {
        val stations = mutableListOf<Station>()
        var currentName: String? = null

        for (line in rawContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                val commaIdx = trimmed.lastIndexOf(',')
                if (commaIdx > 0) {
                    currentName = trimmed.substring(commaIdx + 1)
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val name = currentName ?: trimmed.split('/').lastOrNull()?.replace(".m3u8", "") ?: "Unknown"
                stations.add(Station(name, trimmed))
                currentName = null
            }
        }

        return stations
    }

    fun loadFromAssets(content: String): List<Station> {
        return parse(content)
    }
}
