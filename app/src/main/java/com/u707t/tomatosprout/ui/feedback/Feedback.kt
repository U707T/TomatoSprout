package com.u707t.tomatosprout.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import com.u707t.tomatosprout.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一的触感 + 操作音效。
 *
 * 为什么不用 `View.performHapticFeedback(CLOCK_TICK)`：
 * 很多 ROM 对 CLOCK_TICK 返回成功但不振动，用户体感就是「一点反馈都没有」。
 * 这里直接用 Vibrator（VIBRATE 是普通权限，无运行时弹窗），
 * 并用 SoundPool 播放 3 个很短的本地音效，统一受 App 内两个开关控制。
 */
@Singleton
class FeedbackController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ui_feedback", Context.MODE_PRIVATE)

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val tapSound = soundPool.load(context, R.raw.ui_tap, 1)
    private val doneSound = soundPool.load(context, R.raw.ui_done, 1)
    private val errorSound = soundPool.load(context, R.raw.ui_error, 1)

    /** App 启动时调用一次，触发 SoundPool 预加载。 */
    fun warmUp() = Unit

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit { putBoolean(KEY_HAPTICS, enabled) }
        if (enabled) confirm()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit { putBoolean(KEY_SOUND, enabled) }
        if (enabled) play(doneSound, 0.45f)
    }

    /** 普通点击：短振动 + 轻微木质点击声。 */
    fun tap() {
        val effect = if (hasAmplitude()) {
            VibrationEffect.createOneShot(16, 120)
        } else {
            VibrationEffect.createOneShot(16, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrate(effect)
        play(tapSound, 0.30f)
    }

    /** 成功：打卡 / 创建 / 完成。 */
    fun confirm() {
        vibratePattern(
            longArrayOf(0, 16, 45, 22),
            intArrayOf(0, 140, 0, 190),
        )
        play(doneSound, 0.45f)
    }

    /** 失败 / 删除 / 拒绝：更低、更短的两次振动。 */
    fun reject() {
        vibratePattern(
            longArrayOf(0, 22, 55, 26),
            intArrayOf(0, 170, 0, 215),
        )
        play(errorSound, 0.40f)
    }

    private fun play(soundId: Int, volume: Float) {
        if (!_soundEnabled.value || soundId == 0) return
        runCatching { soundPool.play(soundId, volume, volume, 1, 0, 1f) }
    }

    private fun vibrate(effect: VibrationEffect) {
        if (!_hapticsEnabled.value) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect) }
    }

    private fun hasAmplitude(): Boolean = vibrator?.hasAmplitudeControl() == true

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        val effect = if (hasAmplitude()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrate(effect)
    }

    private companion object {
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_SOUND = "sound_enabled"
    }
}

val LocalFeedback = staticCompositionLocalOf<FeedbackController?> { null }
