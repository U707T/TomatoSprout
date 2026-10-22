package com.u707t.tomatosprout.ui.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 专注页的显示与音频偏好：常亮、亮度、沉浸、白噪音。
 * 从 ViewModel 抽出后，结算 / 计时逻辑不再被这些 UI 状态干扰。
 */
class FocusPreferencesController(
    private val audio: FocusAudioPlayer,
) {
    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _brightness = MutableStateFlow(-1f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _immersive = MutableStateFlow(false)
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    private val _audioTrack = MutableStateFlow(FocusAudioOption.NONE)
    val audioTrack: StateFlow<FocusAudioOption> = _audioTrack.asStateFlow()

    private val _audioPlaying = MutableStateFlow(false)
    val audioPlaying: StateFlow<Boolean> = _audioPlaying.asStateFlow()

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
    }

    fun setBrightness(value: Float) {
        _brightness.value = value.coerceIn(0.1f, 1f)
    }

    fun toggleImmersive() {
        _immersive.value = !_immersive.value
    }

    fun toggleAudio() {
        if (_audioPlaying.value) {
            audio.pause()
            _audioPlaying.value = false
        } else {
            val option = _audioTrack.value.takeIf { it != FocusAudioOption.NONE }
                ?: FocusAudioOption.WHITE_NOISE
            _audioTrack.value = option
            audio.play(option)
            _audioPlaying.value = true
        }
    }

    fun selectAudio(option: FocusAudioOption) {
        _audioTrack.value = option
        if (option == FocusAudioOption.NONE) {
            audio.stop()
            _audioPlaying.value = false
        } else {
            audio.play(option)
            _audioPlaying.value = true
        }
    }

    fun stopAudio() {
        audio.stop()
        _audioPlaying.value = false
        _audioTrack.value = FocusAudioOption.NONE
    }

    fun release() {
        audio.release()
    }
}
