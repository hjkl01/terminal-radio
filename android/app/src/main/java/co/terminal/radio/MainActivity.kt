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
    private var currentStationUrl: String? = null
    private var playingAnimationHandler: android.os.Handler? = null
    private var playingAnimationRunnable: Runnable? = null

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
        adapter = StationAdapter({ station ->
            binding.tvTitle.text = station.name
            playStation(station.url)
        })
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

            val parsedStations = withContext(Dispatchers.IO) {
                M3uParser.loadFromAssets(content)
            }

            withContext(Dispatchers.Main) {
                stations = parsedStations
                adapter.submitList(stations)

                // Auto-play "音乐之声" or first station
                val defaultStation = stations.indexOfFirst { it.name == "音乐之声" }
                    .takeIf { it >= 0 } ?: 0
                val station = stations[defaultStation]
                binding.tvTitle.text = station.name
                playStation(station.url)
            }
        }
    }

    private fun playStation(url: String) {
        val app = application as RadioApplication
        try {
            currentStationUrl = url
            app.playStation(url)
            adapter.currentPlayingUrl = url
            updatePlayPauseButton(true)
            showPlayingStatus(true)
            startPlayingAnimation()
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause
            else R.drawable.ic_play
        )
        showPlayingStatus(isPlaying)
    }

    private fun showPlayingStatus(isPlaying: Boolean) {
        binding.tvStatus.text = if (isPlaying) "播放中" else "已暂停"
        binding.tvStatus.visibility = View.VISIBLE
        binding.ivPlayingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE
    }

    private fun startPlayingAnimation() {
        stopPlayingAnimation()
        playingAnimationHandler = android.os.Handler(mainLooper)
        playingAnimationRunnable = object : Runnable {
            override fun run() {
                val alpha = if (binding.ivPlayingIndicator.alpha == 1f) 0.3f else 1f
                binding.ivPlayingIndicator.animate()
                    .alpha(alpha)
                    .setDuration(800)
                    .start()
                playingAnimationHandler?.postDelayed(this, 1000)
            }
        }
        playingAnimationHandler?.postDelayed(playingAnimationRunnable!!, 0)
    }

    private fun stopPlayingAnimation() {
        playingAnimationHandler?.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        val app = application as RadioApplication
        updatePlayPauseButton(app.player.isPlaying)
    }

    override fun onPause() {
        super.onPause()
        stopPlayingAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayingAnimation()
        val app = application as RadioApplication
        app.stop()
    }
}
