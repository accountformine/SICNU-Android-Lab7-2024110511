package cn.edu.sicnu.guoxiangyu2024110511.musicplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), ServiceConnection {

    companion object {
        fun formatDurationText(ms: Int): Pair<Int, Int> {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            return Pair(totalSeconds / 60, totalSeconds % 60)
        }
    }

    private lateinit var lvMusic: ListView
    private lateinit var tvSongName: TextView
    private lateinit var tvTrackCount: TextView
    private lateinit var tvTimeProgress: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPause: Button

    private val trackItems = mutableListOf<String>()
    private lateinit var listAdapter: ArrayAdapter<String>
    private var binder: MusicService.MusicBinder? = null
    @Volatile
    private var isSeeking = false
    @Volatile
    private var isRunning = true

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        binder?.refreshMusicList()
        refreshTrackListView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lvMusic = findViewById(R.id.lvMusic)
        tvSongName = findViewById(R.id.tvSongName)
        tvTrackCount = findViewById(R.id.tvTrackCount)
        tvTimeProgress = findViewById(R.id.tvTimeProgress)
        seekBar = findViewById(R.id.seekBar)
        btnPause = findViewById(R.id.btnPause)

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, trackItems)
        lvMusic.adapter = listAdapter
        lvMusic.setOnItemClickListener { _, _, position, _ ->
            if ((binder?.size ?: 0) > 0) {
                sendServiceCommand(MusicService.CMD_PLAY_AT, position)
            }
        }

        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            sendServiceCommand(MusicService.CMD_PLAY)
        }
        btnPause.setOnClickListener {
            sendServiceCommand(MusicService.CMD_PAUSE)
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            sendServiceCommand(MusicService.CMD_STOP)
        }
        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            sendServiceCommand(MusicService.CMD_PREV)
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            sendServiceCommand(MusicService.CMD_NEXT)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val (curMin, curSec) = formatDurationText(progress)
                    val (durMin, durSec) = formatDurationText(bar?.max ?: 0)
                    val curStr = getString(R.string.time_mmss_format, curMin, curSec)
                    val durStr = getString(R.string.time_mmss_format, durMin, durSec)
                    tvTimeProgress.text = getString(R.string.time_progress_format, curStr, durStr)
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                isSeeking = false
                if (bar != null) {
                    binder?.currentPosition = bar.progress
                }
            }
        })

        requestRequiredPermissions()
        startAndBindMusicService()
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startAndBindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    private fun sendServiceCommand(cmd: Int, index: Int = -1) {
        val intent = Intent(this, MusicService::class.java).apply {
            putExtra(MusicService.COMMAND, cmd)
            if (index >= 0) {
                putExtra(MusicService.EXTRA_INDEX, index)
            }
        }
        startService(intent)
    }

    private fun refreshTrackListView() {
        val names = binder?.trackNames ?: emptyList()
        trackItems.clear()
        if (names.isEmpty()) {
            trackItems.add(getString(R.string.empty_music_list))
        } else {
            trackItems.addAll(names)
        }
        listAdapter.notifyDataSetChanged()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        binder = service as? MusicService.MusicBinder
        binder?.refreshMusicList()
        refreshTrackListView()

        thread {
            while (isRunning && binder != null) {
                val b = binder ?: break
                val totalCount = b.size
                val currentIdx = b.currentIndex
                val songName = b.musicName
                val duration = b.duration
                val position = b.currentPosition
                val isPaused = b.isPausedState

                runOnUiThread {
                    if (totalCount == 0) {
                        tvSongName.text = getString(R.string.no_song_playing)
                        tvTrackCount.text = getString(R.string.track_empty_count)
                        seekBar.max = 0
                        seekBar.progress = 0
                        val zero = getString(R.string.time_mmss_format, 0, 0)
                        tvTimeProgress.text = getString(R.string.time_progress_format, zero, zero)
                    } else {
                        tvSongName.text = songName
                        tvTrackCount.text = getString(R.string.track_count_format, currentIdx + 1, totalCount)
                        if (!isSeeking) {
                            seekBar.max = duration
                            seekBar.progress = position
                            val (curMin, curSec) = formatDurationText(position)
                            val (durMin, durSec) = formatDurationText(duration)
                            val curStr = getString(R.string.time_mmss_format, curMin, curSec)
                            val durStr = getString(R.string.time_mmss_format, durMin, durSec)
                            tvTimeProgress.text = getString(R.string.time_progress_format, curStr, durStr)
                        }
                    }
                    btnPause.text = if (isPaused) {
                        getString(R.string.btn_resume)
                    } else {
                        getString(R.string.btn_pause)
                    }
                }
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        binder = null
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        if (binder != null) {
            unbindService(this)
            binder = null
        }
    }
}
