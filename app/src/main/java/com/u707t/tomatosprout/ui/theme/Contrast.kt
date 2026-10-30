package com.u707t.tomatosprout.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 在一块「用户自选色」上放文字/图标时，按亮度挑一个能看清的前景。
 *
 * 任务色板里有 `#F2CE68`（亮黄）和 `#8C7A70`（深棕）这种跨度很大的颜色，
 * 固定白字在亮黄上只有 1.3:1，固定深字在深棕上也不够。统一走这里。
 */
fun Color.readableContentColor(dark: Color = Color(0xFF3A2A24)): Color =
    if (luminance() > 0.55f) dark else Color.White
