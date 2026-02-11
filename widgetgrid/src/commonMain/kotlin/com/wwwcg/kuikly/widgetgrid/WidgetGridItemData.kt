package com.wwwcg.kuikly.widgetgrid

import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.CallbackRef

/**
 * 网格卡片数据基类
 *
 * 业务方可以继承此类来添加自定义属性（如标题、图标颜色等），
 * 自定义属性建议使用 [observable] 委托以支持响应式更新。
 *
 * 示例：
 * ```
 * class MyCardData(scope: PagerScope) : WidgetGridItemData(scope) {
 *     var title: String by observable("")
 *     var iconColor: Color by observable(Color.BLUE)
 * }
 * ```
 *
 * @param scope PagerScope，通常传入 Pager 的 this
 */
open class WidgetGridItemData(scope: PagerScope) : BaseObject(), PagerScope by scope {

    /** 卡片唯一标识 */
    open var id: Int = 0

    /** 卡片横向占位格数，1 = 1x1，2 = 2x1 */
    open var spanX: Int by observable(1)

    // ==================== 内部状态（由 WidgetGrid 管理，外部请勿修改） ====================

    internal var offsetX: Float by observable(0f)
    internal var offsetY: Float by observable(0f)
    internal var isDragging: Boolean by observable(false)
    internal var needsAnimation: Boolean by observable(false)
    internal var animationKey: Int by observable(0)
    internal var shakeAngle: Float = 0f
    internal var shakeKey: Int by observable(0)
    internal var isTouching: Boolean by observable(false)
    internal var longPressCallback: CallbackRef? = null

    // 点击检测辅助状态（非响应式）
    internal var wasPanned: Boolean = false
    internal var longPressFired: Boolean = false
    internal var buttonClicked: Boolean = false
}
