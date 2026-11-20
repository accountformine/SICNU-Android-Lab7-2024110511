package cn.edu.sicnu.guoxiangyu2024110511.musicplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPlayerUnitTest {

    @Test
    fun nextAndPrevTrackIndex_wrapsCircularly() {
        assertEquals(1, MusicService.nextTrackIndex(0, 3))
        assertEquals(0, MusicService.nextTrackIndex(2, 3))
        assertEquals(2, MusicService.prevTrackIndex(0, 3))
        assertEquals(1, MusicService.prevTrackIndex(2, 3))
        assertEquals(0, MusicService.nextTrackIndex(0, 0))
        assertEquals(0, MusicService.prevTrackIndex(0, 0))
    }

    @Test
    fun formatDurationText_convertsMillisToMinutesAndSeconds() {
        assertEquals(Pair(0, 0), MainActivity.formatDurationText(0))
        assertEquals(Pair(1, 5), MainActivity.formatDurationText(65000))
        assertEquals(Pair(3, 45), MainActivity.formatDurationText(225000))
    }
}
