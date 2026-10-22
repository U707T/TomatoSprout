package com.u707t.tomatosprout.di

import javax.inject.Qualifier

/**
 * 应用级协程作用域：只用于「用户已经完成动作，结果必须落库」的收尾工作。
 *
 * 典型场景是专注结算：用户点完「停止 / 记录」后马上退出页面，ViewModel 会被清理，
 * 挂在 viewModelScope 上的写入可能来不及执行。结算写入不依赖任何界面状态，
 * 因此交给应用级作用域，保证 FocusSession / CheckIn 一定落库。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
