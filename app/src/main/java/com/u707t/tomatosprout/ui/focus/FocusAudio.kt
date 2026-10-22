package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.media.MediaPlayer
import com.u707t.tomatosprout.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 循环素材必须本身无缝：raw 里的白噪音 / 棕色噪音 / 树叶风声已裁掉头尾淡变并做等功率交叉淡化。
 * 如果换成带淡入淡出的素材，isLooping = true 会每隔几秒把音量拉到 0，听感是「一阵一阵」。
 */
enum class FocusAudioOption(val label: String, val resId: Int?) {
    NONE("无音乐", null),
    WHITE_NOISE("白噪音", R.raw.white_noise),
    BROWN_NOISE("棕色噪音", R.raw.brown_noise),
    WIND("树叶风声", R.raw.wind_leaf),
    FIRE("篝火", R.raw.bonfire_pure),
    PIANO("钢琴", R.raw.piano),
    MUSIC_BOX("八音盒", R.raw.music_box),
}

/**
 * 本地 raw 音频循环播放，无网络、无第三方播放器依赖。
 *
 * 循环素材必须本身可无缝循环：不能带淡入淡出，首尾电平要连续。
 * 否则 isLooping 会把淡变一起循环，听感就是「一阵一阵」的断音。
 * 白噪音 / 棕色噪音已按此重制（裁掉淡变 + 首尾等功率交叉淡化）。
 */
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
