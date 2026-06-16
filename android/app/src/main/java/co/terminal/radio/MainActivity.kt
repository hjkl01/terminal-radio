package co.terminal.radio

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import co.terminal.radio.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StationAdapter
    private var stations: List<Station> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupButtons()
        loadStations()
    }

    private fun setupRecyclerView() {
        adapter = StationAdapter { station ->
            binding.tvTitle.text = station.name
            playStation(station.url)
        }
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnPrevious.setOnClickListener {
            val app = application as RadioApplication
            app.playPrevious()
        }

        binding.btnPlayPause.setOnClickListener {
            val app = application as RadioApplication
            app.togglePlayPause()
            updatePlayPauseButton(app.player.isPlaying)
        }

        binding.btnNext.setOnClickListener {
            val app = application as RadioApplication
            app.playNext()
        }
    }

    private fun loadStations() {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                assets.open("cnr.m3u").use { input ->
                    input.bufferedReader().readText()
                }
            }

            stations = M3uParser.loadFromAssets(content)
            adapter.submitList(stations)

            // Auto-play "音乐之声" or first station
            val defaultStation = stations.indexOfFirst { it.name == "音乐之声" }
                .takeIf { it >= 0 } ?: 0
            val station = stations[defaultStation]
            binding.tvTitle.text = station.name
            playStation(station.url)
        }
    }

    private fun playStation(url: String) {
        val app = application as RadioApplication
        try {
            app.playStation(url)
            updatePlayPauseButton(true)
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    override fun onResume() {
        super.onResume()
        val app = application as RadioApplication
        updatePlayPauseButton(app.player.isPlaying)
    }

    override fun onDestroy() {
        super.onDestroy()
        val app = application as RadioApplication
        app.stop()
    }
}
