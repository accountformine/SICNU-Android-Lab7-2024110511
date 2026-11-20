package cn.edu.sicnu.guoxiangyu2024110511.musicplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MusicService : Service() {

    companion object {
        const val COMMAND = "Operate"
        const val EXTRA_INDEX = "Index"
        const val CMD_PLAY = 1
        const val CMD_PAUSE = 2
        const val CMD_STOP = 3
        const val CMD_NEXT = 4
        const val CMD_PREV = 5
        const val CMD_PLAY_AT = 6
        const val CHANNEL_ID = "music_channel"
        const val NOTIFICATION_ID = 1001

        fun nextTrackIndex(current: Int, size: Int): Int {
            if (size <= 0) return 0
            return (current + 1) % size
        }

        fun prevTrackIndex(current: Int, size: Int): Int {
            if (size <= 0) return 0
            return (current - 1 + size) % size
        }
    }

    private val mediaPlayer = MediaPlayer()
    private val musicUriList = mutableListOf<Uri>()
    private val musicNameList = mutableListOf<String>()
    private var current = 0
    private var isPause = false
    private var isPrepared = false
    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val musicName: String
            get() = if (musicNameList.isNotEmpty()) musicNameList[current] else ""

        var currentPosition: Int
            get() = if (isPrepared) mediaPlayer.currentPosition else 0
            set(value) {
                if (isPrepared) {
                    mediaPlayer.seekTo(value)
                }
            }

        val duration: Int
            get() = if (isPrepared) mediaPlayer.duration else 0

        val size: Int
            get() = musicUriList.size

        val currentIndex: Int
            get() = current

        val isPausedState: Boolean
            get() = isPause

        val isPlayingState: Boolean
            get() = isPrepared && mediaPlayer.isPlaying

        val trackNames: List<String>
            get() = musicNameList.toList()

        fun refreshMusicList() {
            loadMusicList()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaPlayer.setOnPreparedListener {
            isPrepared = true
            it.start()
        }
        mediaPlayer.setOnCompletionListener {
            next()
        }
        loadMusicList()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra(COMMAND, 0) ?: 0) {
            CMD_PLAY -> play()
            CMD_PAUSE -> pause()
            CMD_STOP -> stop()
            CMD_NEXT -> next()
            CMD_PREV -> prev()
            CMD_PLAY_AT -> {
                val idx = intent?.getIntExtra(EXTRA_INDEX, 0) ?: 0
                playAt(idx)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
    }

    fun loadMusicList() {
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, audioPermission) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        musicUriList.clear()
        musicNameList.clear()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: continue
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                musicUriList.add(uri)
                musicNameList.add(title)
            }
        }
        if (current >= musicUriList.size) {
            current = 0
        }
    }

    private fun play() {
        if (musicUriList.isEmpty()) {
            loadMusicList()
        }
        if (musicUriList.isEmpty()) return

        if (isPause && isPrepared) {
            mediaPlayer.start()
            isPause = false
            return
        }
        try {
            isPrepared = false
            isPause = false
            mediaPlayer.reset()
            mediaPlayer.setDataSource(applicationContext, musicUriList[current])
            mediaPlayer.prepareAsync()
            showNotification(musicNameList[current])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playAt(index: Int) {
        if (index !in musicUriList.indices) return
        current = index
        isPause = false
        isPrepared = false
        play()
    }

    private fun pause() {
        if (!isPrepared) return
        if (isPause) {
            mediaPlayer.start()
            isPause = false
        } else if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPause = true
        }
    }

    private fun stop() {
        if (isPrepared) {
            mediaPlayer.stop()
            isPrepared = false
            isPause = false
        }
    }

    private fun next() {
        if (musicUriList.isEmpty()) return
        current = nextTrackIndex(current, musicUriList.size)
        isPause = false
        isPrepared = false
        play()
    }

    private fun prev() {
        if (musicUriList.isEmpty()) return
        current = prevTrackIndex(current, musicUriList.size)
        isPause = false
        isPrepared = false
        play()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(songTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(songTitle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
