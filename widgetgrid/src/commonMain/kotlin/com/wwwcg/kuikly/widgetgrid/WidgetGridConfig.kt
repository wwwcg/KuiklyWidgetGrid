package com.wwwcg.kuikly.widgetgrid

import com.tencent.kuikly.core.base.Color

/**
 * 小组件网格的配置参数
 *
 * @property columnCount 列数，默认 3
 * @property cardHeight 卡片高度（dp），默认 100
 * @property cardSpacing 卡片间距（dp），默认 12
 * @property dragScaleRatio 拖拽时卡片放大比例，默认 1.05
 * @property dragOpacity 拖拽时卡片透明度，默认 0.9
 * @property dragAnimationDuration 拖拽过程中其他卡片位移动画时长（秒），默认 0.3
 * @property shakeEnabled 是否启用编辑态抖动效果，默认 true
 * @property shakeInterval 抖动切换间隔（毫秒），默认 200
 * @property shakeAngleBase 基础抖动角度（度），默认 1.2
 * @property shakeAngleOffset 相邻卡片角度偏移（度），让抖动更自然，默认 0.5
 * @property shakeAnimationDuration 抖动动画时长（秒），默认 0.2
 * @property longPressDelay 长按触发延迟（毫秒），默认 350
 * @property cardBackgroundColor 卡片默认背景色
 * @property cardBorderRadius 卡片圆角半径（dp），默认 16
 * @property deleteButtonSize 删除按钮尺寸（dp），默认 24
 * @property deleteButtonOffset 删除按钮相对卡片左上角的偏移（dp），负值表示向外延伸，默认 -8
 * @property deleteButtonColor 删除按钮背景色
 */
data class WidgetGridConfig(
    val columnCount: Int = 3,
    val cardHeight: Float = 100f,
    val cardSpacing: Float = 12f,
    val dragScaleRatio: Float = 1.05f,
    val dragOpacity: Float = 0.9f,
    val dragAnimationDuration: Float = 0.3f,
    val shakeEnabled: Boolean = true,
    val shakeInterval: Int = 200,
    val shakeAngleBase: Float = 1.2f,
    val shakeAngleOffset: Float = 0.5f,
    val shakeAnimationDuration: Float = 0.2f,
    val longPressDelay: Int = 350,
    val cardBackgroundColor: Color = Color(0xFF2C2C2EL),
    val cardBorderRadius: Float = 16f,
    val deleteButtonSize: Float = 24f,
    val deleteButtonOffset: Float = -8f,
    val deleteButtonColor: Color = Color(0xFFFF3B30L),
)
