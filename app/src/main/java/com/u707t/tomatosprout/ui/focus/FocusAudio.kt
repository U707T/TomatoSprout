package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.media.MediaPlayer
import com.u707t.tomatosprout.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class FocusAudioOption(val label: String, val resId: Int?) {
    NONE("无音乐", null),
    WHITE_NOISE("白噪音", R.raw.white_noise),
    BROWN_NOISE("棕色噪音", R.raw.brown_noise),
}

/** 本地 raw 音频循环播放，无网络、无第三方播放器依赖。 */
@Singleton
class FocusAudioPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: MediaPlayer? = null
    var current: FocusAudioOption = FocusAudioOption.NONE
        private set

    fun play(option: FocusAudioOption) {
        if (option == FocusAudioOption.NONE) {
            stop()
            return
        }
        if (current == option && player != null) {
            runCatching { player?.start() }
            return
        }
        stop()
        current = option
        val res = option.resId ?: return
        runCatching {
            player = MediaPlayer.create(context, res)?.apply {
                isLooping = true
                setVolume(0.55f, 0.55f)
                start()
            }
        }
    }

    fun pause() {
        runCatching { if (player?.isPlaying == true) player?.pause() }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        current = FocusAudioOption.NONE
    }

    fun release() = stop()
}
