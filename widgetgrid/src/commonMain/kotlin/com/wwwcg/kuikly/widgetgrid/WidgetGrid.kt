package com.wwwcg.kuikly.widgetgrid

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.EventName
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.CallbackRef
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.max

// ==================== 内部位置数据 ====================

internal data class GridPosition(
    val x: Float,
    val y: Float,
    val row: Int,
    val col: Int,
)

// ==================== WidgetGridAttr ====================

/**
 * WidgetGrid 组件的属性
 */
class WidgetGridAttr : ComposeAttr() {

    /** 网格配置 */
    var config: WidgetGridConfig = WidgetGridConfig()

    /** 是否处于编辑模式（外部控制） */
    var editing: Boolean by observable(false)

    /** 网格可用宽度（dp），通常为 pageViewWidth - 左右边距 */
    var gridWidth: Float = 0f

    /** 卡片内容构建器，业务方在此渲染每个卡片的内容 */
    internal var _cardContentBuilder: (ViewContainer<*, *>.(WidgetGridItemData) -> Unit)? = null

    /**
     * 设置卡片内容构建器
     *
     * 示例：
     * ```
     * cardContent { item ->
     *     val myItem = item as MyCardData
     *     View {
     *         attr { flex(1f); padding(12f) }
     *         Text { attr { text(myItem.title) } }
     *     }
     * }
     * ```
     */
    fun cardContent(builder: ViewContainer<*, *>.(WidgetGridItemData) -> Unit) {
        _cardContentBuilder = builder
    }
}

// ==================== WidgetGridEvent ====================

/**
 * WidgetGrid 组件的事件
 */
class WidgetGridEvent : ComposeEvent() {

    private var _onEditingChanged: ((Boolean) -> Unit)? = null
    private var _onReorder: ((fromIndex: Int, toIndex: Int) -> Unit)? = null
    private var _onDelete: ((WidgetGridItemData) -> Unit)? = null
    private var _onCardClick: ((WidgetGridItemData) -> Unit)? = null
    private var _onResize: ((item: WidgetGridItemData, oldSpanX: Int, newSpanX: Int) -> Unit)? = null

    /** 编辑状态变化（如长按触发编辑态） */
    fun onEditingChanged(handler: (Boolean) -> Unit) {
        _onEditingChanged = handler
    }

    /** 卡片重新排序完成 */
    fun onReorder(handler: (fromIndex: Int, toIndex: Int) -> Unit) {
        _onReorder = handler
    }

    /** 卡片被删除 */
    fun onDelete(handler: (WidgetGridItemData) -> Unit) {
        _onDelete = handler
    }

    /**
     * 卡片被点击（非拖拽、非长按触发）
     *
     * 编辑态和非编辑态下均会触发。
     */
    fun onCardClick(handler: (WidgetGridItemData) -> Unit) {
        _onCardClick = handler
    }

    /**
     * 卡片尺寸切换（编辑态点击右上角切换按钮触发）
     *
     * 可在回调中根据 newSpanX 更新卡片业务数据。
     *
     * @param handler 回调参数：item（卡片数据）、oldSpanX（旧尺寸）、newSpanX（新尺寸）
     */
    fun onResize(handler: (item: WidgetGridItemData, oldSpanX: Int, newSpanX: Int) -> Unit) {
        _onResize = handler
    }

    internal fun fireEditingChanged(editing: Boolean) {
        _onEditingChanged?.invoke(editing)
    }

    internal fun fireReorder(from: Int, to: Int) {
        _onReorder?.invoke(from, to)
    }

    internal fun fireDelete(item: WidgetGridItemData) {
        _onDelete?.invoke(item)
    }

    internal fun fireCardClick(item: WidgetGridItemData) {
        _onCardClick?.invoke(item)
    }

    internal fun fireResize(item: WidgetGridItemData, oldSpanX: Int, newSpanX: Int) {
        _onResize?.invoke(item, oldSpanX, newSpanX)
    }
}

// ==================== WidgetGridView ====================

/**
 * 类似 iPhone 负一屏的卡片式拖动排序组件
 *
 * 功能：
 * - 支持 1x1 和 2x1 两种卡片尺寸
 * - 网格布局可配置（列数、间距、高度等）
 * - 拖拽排序，带"推挤"其他卡片自动让位效果
 * - 2x1 卡片目标位置放不下时自动换行
 * - 长按进入编辑态（抖动效果）
 * - 编辑态下删除卡片
 * - 卡片内容由业务方自定义
 *
 * 使用方式：
 * ```
 * WidgetGrid {
 *     ref { gridRef = it }
 *     attr {
 *         config = WidgetGridConfig(columnCount = 3, cardHeight = 100f)
 *         gridWidth = pagerData.pageViewWidth - 32f
 *         editing = myEditingState
 *         cardContent { item ->
 *             // 自定义卡片内容
 *         }
 *     }
 *     event {
 *         onEditingChanged { editing -> myEditingState = editing }
 *         onReorder { from, to -> }
 *         onDelete { item -> }
 *     }
 * }
 *
 * // 在 viewDidLoad 中添加卡片
 * gridRef.view?.addItem(MyCardData(this).apply { id = 1; spanX = 1 })
 * ```
 */
class WidgetGridView : ComposeView<WidgetGridAttr, WidgetGridEvent>() {

    companion object {
        private const val TAG = "WidgetGrid"
    }

    // ==================== 内部数据列表 ====================

    internal var cardList by observableList<WidgetGridItemData>()

    // ==================== 拖拽状态（非响应式） ====================

    private var isDragging = false
    private var dragCardData: WidgetGridItemData? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var lastTargetIndex = -1

    // ==================== 抖动动画状态 ====================

    private var shakeTimerRef: CallbackRef? = null
    private var shakeDirection = 1

    // ==================== 编辑态追踪 ====================

    private var lastEditingState = false

    // ==================== 公开 API ====================

    /** 添加单个卡片 */
    fun addItem(item: WidgetGridItemData) {
        cardList.add(item)
    }

    /** 批量添加卡片 */
    fun addItems(items: List<WidgetGridItemData>) {
        cardList.addAll(items)
    }

    /** 根据 id 移除卡片（带动画） */
    fun removeItem(id: Int) {
        val item = cardList.find { it.id == id } ?: return
        deleteCard(item)
    }

    /** 获取当前卡片列表的副本 */
    fun getItems(): List<WidgetGridItemData> = cardList.toList()

    /**
     * 设置编辑状态
     * 也可通过 attr.editing 从外部响应式控制
     */
    fun setEditing(editing: Boolean) {
        setEditingInternal(editing)
        event.fireEditingChanged(editing)
    }

    /**
     * 切换卡片尺寸（带平滑动画）
     *
     * 修改 [item] 的 [WidgetGridItemData.spanX]，并对受影响的卡片执行位移动画过渡。
     *
     * 示例：点击 1×1 切换为 2×1
     * ```
     * gridRef.view?.resizeCard(item, if (item.spanX == 1) 2 else 1)
     * ```
     *
     * @param item 要调整尺寸的卡片
     * @param newSpanX 新的横向占位格数
     */
    fun resizeCard(item: WidgetGridItemData, newSpanX: Int) {
        val oldSpanX = item.spanX
        if (oldSpanX == newSpanX) return
        val index = cardList.indexOf(item)
        if (index < 0) return

        // Android 平台：直接更新，不使用位置动画（避免动画冲突）
        if (pagerData.isAndroid) {
            item.spanX = newSpanX
            event.fireResize(item, oldSpanX, newSpanX)
            return
        }

        // 其他平台：保存旧位置，更新后用 offset 动画平滑过渡
        val oldPositions = cardList.mapIndexed { i, card ->
            card to calculateCardPosition(i)
        }.toMap()

        item.spanX = newSpanX
        event.fireResize(item, oldSpanX, newSpanX)

        cardList.forEachIndexed { newIndex, card ->
            val oldPos = oldPositions[card] ?: return@forEachIndexed
            val newPos = calculateCardPosition(newIndex)

            card.offsetX = oldPos.x - newPos.x
            card.offsetY = oldPos.y - newPos.y
            card.needsAnimation = true
            card.animationKey++
        }

        setTimeout(16) {
            cardList.forEach { card ->
                card.offsetX = 0f
                card.offsetY = 0f
            }

            setTimeout((config.dragAnimationDuration * 1000).toInt() + 50) {
                cardList.forEach { card ->
                    card.needsAnimation = false
                }
            }
        }
    }

    // ==================== 配置快捷访问 ====================

    private val config: WidgetGridConfig get() = attr.config

    /** 删除按钮向左溢出的距离 */
    private val leftOverflow: Float get() = max(0f, -config.deleteButtonOffset)
    /** 缩放按钮向右溢出的距离 */
    private val rightOverflow: Float get() = if (config.resizeEnabled) max(0f, -config.resizeButtonOffset) else 0f
    /** 按钮向上溢出的距离 */
    private val topOverflow: Float get() = max(leftOverflow, rightOverflow)

    private fun getCardWidth(): Float {
        return (attr.gridWidth - config.cardSpacing * (config.columnCount - 1)) / config.columnCount
    }

    private fun getItemWidth(item: WidgetGridItemData): Float {
        return if (item.spanX == 2) getCardWidth() * 2 + config.cardSpacing else getCardWidth()
    }

    // ==================== 布局计算 ====================

    private fun calculateCardPosition(index: Int): GridPosition {
        if (index < 0 || index >= cardList.size) {
            return GridPosition(0f, 0f, 0, 0)
        }

        var currentRow = 0
        var currentCol = 0

        for (i in 0 until index) {
            if (i >= cardList.size) break
            val card = cardList[i]
            val spanX = card.spanX

            if (currentCol + spanX > config.columnCount) {
                currentRow++
                currentCol = 0
            }

            currentCol += spanX
            if (currentCol >= config.columnCount) {
                currentRow++
                currentCol = 0
            }
        }

        val currentCard = cardList.getOrNull(index) ?: return GridPosition(0f, 0f, currentRow, currentCol)
        if (currentCol + currentCard.spanX > config.columnCount) {
            currentRow++
            currentCol = 0
        }

        val x = currentCol * (getCardWidth() + config.cardSpacing)
        val y = currentRow * (config.cardHeight + config.cardSpacing)

        return GridPosition(x, y, currentRow, currentCol)
    }

    private fun calculateTotalRows(): Int {
        if (cardList.isEmpty()) return 1

        var currentRow = 0
        var currentCol = 0

        for (card in cardList) {
            val spanX = card.spanX

            if (currentCol + spanX > config.columnCount) {
                currentRow++
                currentCol = 0
            }

            currentCol += spanX
            if (currentCol >= config.columnCount) {
                currentRow++
                currentCol = 0
            }
        }

        if (currentCol > 0) {
            currentRow++
        }

        return max(currentRow, 1)
    }

    private fun calculatePositionInNewOrder(newOrder: List<Int>, positionIndex: Int): GridPosition {
        var row = 0
        var col = 0

        for (i in 0 until positionIndex) {
            if (i >= newOrder.size) break
            val cardIndex = newOrder[i]
            if (cardIndex >= cardList.size) continue

            val card = cardList[cardIndex]
            val spanX = card.spanX

            if (col + spanX > config.columnCount) {
                row++
                col = 0
            }

            col += spanX
            if (col >= config.columnCount) {
                row++
                col = 0
            }
        }

        if (positionIndex < newOrder.size) {
            val cardIndex = newOrder[positionIndex]
            if (cardIndex < cardList.size) {
                val card = cardList[cardIndex]
                if (col + card.spanX > config.columnCount) {
                    row++
                    col = 0
                }
            }
        }

        val x = col * (getCardWidth() + config.cardSpacing)
        val y = row * (config.cardHeight + config.cardSpacing)

        return GridPosition(x, y, row, col)
    }

    // ==================== 拖拽处理 ====================

    private fun handleDrag(params: PanGestureParams, cardData: WidgetGridItemData, index: Int) {
        when (params.state) {
            "start" -> {
                startDragging(params, cardData, index)
            }
            "move" -> {
                if (!isDragging) {
                    startDragging(params, cardData, index)
                }
                if (dragCardData != cardData) return

                val deltaX = params.pageX - dragStartX
                val deltaY = params.pageY - dragStartY

                cardData.offsetX = deltaX
                cardData.offsetY = deltaY

                val targetIndex = findTargetIndex(cardData, index, deltaX, deltaY)

                if (targetIndex != lastTargetIndex) {
                    lastTargetIndex = targetIndex
                    previewReorder(index, targetIndex, cardData)
                    KLog.d(TAG, "Target changed: $targetIndex")
                }
            }
            "end" -> {
                if (!isDragging || dragCardData != cardData) return

                val targetIndex = lastTargetIndex

                val currentIndex = cardList.indexOf(cardData)
                if (currentIndex < 0) {
                    KLog.d(TAG, "Drag end: card not found in list")
                    isDragging = false
                    dragCardData = null
                    lastTargetIndex = -1
                    return
                }

                KLog.d(TAG, "Drag end: from=$currentIndex, to=$targetIndex")

                if (targetIndex != currentIndex && targetIndex >= 0 && targetIndex < cardList.size) {
                    val originalList = cardList.toList()

                    val newOrderIndices = originalList.indices.toMutableList()
                    newOrderIndices.removeAt(currentIndex)
                    val insertAt = targetIndex.coerceIn(0, newOrderIndices.size)
                    newOrderIndices.add(insertAt, currentIndex)

                    val reorderedCards = newOrderIndices.map { originalList[it] }

                    cardList.clear()
                    cardList.addAll(reorderedCards)

                    event.fireReorder(currentIndex, targetIndex)

                    KLog.d(TAG, "Reordered: from=$currentIndex, to=$targetIndex")
                }

                cardList.forEach { card ->
                    card.isDragging = false
                    card.offsetX = 0f
                    card.offsetY = 0f
                    card.needsAnimation = false
                }

                isDragging = false
                dragCardData = null
                lastTargetIndex = -1
            }
        }
    }

    private fun startDragging(params: PanGestureParams, cardData: WidgetGridItemData, index: Int) {
        isDragging = true
        dragCardData = cardData
        dragStartX = params.pageX
        dragStartY = params.pageY
        lastTargetIndex = index
        cardData.isDragging = true
        cardData.needsAnimation = false
        KLog.d(TAG, "Drag start: index=$index, card id=${cardData.id}")
    }

    private fun findTargetIndex(cardData: WidgetGridItemData, currentIndex: Int, deltaX: Float, deltaY: Float): Int {
        if (cardList.size <= 1) return currentIndex

        val currentPos = calculateCardPosition(currentIndex)
        val cardW = getItemWidth(cardData)

        val dragCenterX = currentPos.x + deltaX + cardW / 2
        val dragCenterY = currentPos.y + deltaY + config.cardHeight / 2

        val cellWidth = getCardWidth() + config.cardSpacing
        val cellHeight = config.cardHeight + config.cardSpacing

        var row = 0
        var col = 0
        var slotIndex = 0

        for (i in cardList.indices) {
            if (i == currentIndex) continue

            val card = cardList[i]
            val spanX = card.spanX

            if (col + spanX > config.columnCount) {
                row++
                col = 0
            }

            val slotLeftX = col * cellWidth
            val slotCenterY = row * cellHeight + cellHeight / 2

            if (dragCenterY < slotCenterY - cellHeight * 0.4f) {
                return slotIndex
            } else if (dragCenterY < slotCenterY + cellHeight * 0.4f && dragCenterX < slotLeftX + cellWidth * 0.5f) {
                return slotIndex
            }

            col += spanX
            if (col >= config.columnCount) {
                row++
                col = 0
            }
            slotIndex++
        }

        return cardList.size - 1
    }

    private fun previewReorder(fromIndex: Int, toIndex: Int, dragCard: WidgetGridItemData) {
        if (fromIndex == toIndex) {
            cardList.forEach { card ->
                if (card != dragCard && (card.offsetX != 0f || card.offsetY != 0f)) {
                    card.offsetX = 0f
                    card.offsetY = 0f
                    card.needsAnimation = true
                    card.animationKey++
                }
            }
            return
        }

        val withoutDrag = cardList.indices.filter { it != fromIndex }
        val newOrder = withoutDrag.toMutableList()
        val insertAt = toIndex.coerceIn(0, newOrder.size)
        newOrder.add(insertAt, fromIndex)

        KLog.d(TAG, "previewReorder: from=$fromIndex, to=$toIndex, newOrder=$newOrder")

        cardList.forEachIndexed { originalIndex, card ->
            if (card == dragCard) return@forEachIndexed

            val newPositionIndex = newOrder.indexOf(originalIndex)
            if (newPositionIndex < 0) return@forEachIndexed

            val targetPos = calculatePositionInNewOrder(newOrder, newPositionIndex)
            val currentPos = calculateCardPosition(originalIndex)

            val newOffsetX = targetPos.x - currentPos.x
            val newOffsetY = targetPos.y - currentPos.y

            if (card.offsetX != newOffsetX || card.offsetY != newOffsetY) {
                card.offsetX = newOffsetX
                card.offsetY = newOffsetY
                card.needsAnimation = true
                card.animationKey++
            }
        }
    }

    // ==================== 删除 ====================

    private fun deleteCard(cardData: WidgetGridItemData) {
        val deleteIndex = cardList.indexOf(cardData)
        if (deleteIndex < 0) return

        event.fireDelete(cardData)

        // Android 平台：直接删除，不使用位置动画（避免动画冲突问题）
        if (pagerData.isAndroid) {
            cardList.forEach { card ->
                card.offsetX = 0f
                card.offsetY = 0f
                card.shakeAngle = 0f
                card.needsAnimation = false
            }
            cardList.remove(cardData)
            return
        }

        // iOS 及其他平台：使用位置动画实现平滑过渡
        val oldPositions = cardList.mapIndexed { index, card ->
            card to calculateCardPosition(index)
        }.toMap()

        cardList.remove(cardData)

        cardList.forEachIndexed { newIndex, card ->
            val oldPos = oldPositions[card] ?: return@forEachIndexed
            val newPos = calculateCardPosition(newIndex)

            card.offsetX = oldPos.x - newPos.x
            card.offsetY = oldPos.y - newPos.y
            card.shakeAngle = 0f
            // needsAnimation = true 让 attr 块选择弹性动画来过渡位移
            card.needsAnimation = true
            card.animationKey++
        }

        setTimeout(16) {
            cardList.forEach { card ->
                card.offsetX = 0f
                card.offsetY = 0f
            }

            setTimeout((config.dragAnimationDuration * 1000).toInt() + 50) {
                cardList.forEach { card ->
                    card.needsAnimation = false
                }
            }
        }
    }

    // ==================== 抖动动画 ====================

    private fun startShakeAnimation() {
        if (!config.shakeEnabled) return
        stopShakeAnimation()
        shakeDirection = 1
        scheduleNextShake()
    }

    private fun stopShakeAnimation() {
        shakeTimerRef?.let { clearTimeout(it) }
        shakeTimerRef = null
        cardList.forEach { card ->
            card.shakeAngle = 0f
            card.shakeKey++
        }
    }

    private fun scheduleNextShake() {
        shakeTimerRef = setTimeout(config.shakeInterval) {
            shakeDirection = -shakeDirection
            val angle = config.shakeAngleBase * shakeDirection

            cardList.forEachIndexed { index, card ->
                if (!card.isDragging) {
                    val offset = if (index % 2 == 0) config.shakeAngleOffset else -config.shakeAngleOffset
                    card.shakeAngle = angle + offset
                    card.shakeKey++
                }
            }

            if (lastEditingState) {
                scheduleNextShake()
            }
        }
    }

    // ==================== 编辑态管理 ====================

    private fun setEditingInternal(editing: Boolean) {
        if (lastEditingState == editing) return
        lastEditingState = editing
        if (editing) startShakeAnimation() else stopShakeAnimation()
    }

    private fun onCardLongPress() {
        if (!lastEditingState) {
            setEditingInternal(true)
            event.fireEditingChanged(true)
        }
    }

    // ==================== 渲染 ====================

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // 根视图：负 margin 扩展 bounds 以容纳按钮溢出，确保触摸事件可达
            attr {
                ctx.setEditingInternal(ctx.attr.editing)
                width(ctx.attr.gridWidth + ctx.leftOverflow + ctx.rightOverflow)
                marginLeft(-ctx.leftOverflow)
                marginTop(-ctx.topOverflow)
                marginRight(-ctx.rightOverflow)
            }

            // 网格容器（尺寸包含按钮溢出区域）
            View {
                attr {
                    val totalRows = ctx.calculateTotalRows()
                    height(totalRows * (ctx.config.cardHeight + ctx.config.cardSpacing) + ctx.topOverflow)
                    width(ctx.attr.gridWidth + ctx.leftOverflow + ctx.rightOverflow)
                }

                vforIndex({ ctx.cardList }) { cardData, index, _ ->
                    // 外层包装：包含卡片主体 + 按钮溢出空间
                    View {
                        // 基础定位（网格容器已通过负 margin 补偿，wrapper 直接用卡片位置）
                        attr {
                            val currentIndex = ctx.cardList.indexOf(cardData)
                            val pos = if (currentIndex >= 0) ctx.calculateCardPosition(currentIndex) else GridPosition(0f, 0f, 0, 0)
                            absolutePosition(top = pos.y, left = pos.x)
                            size(ctx.getItemWidth(cardData) + ctx.leftOverflow + ctx.rightOverflow, ctx.config.cardHeight + ctx.topOverflow)
                            zIndex(if (cardData.isDragging) 100 else 0)
                        }

                        // 变换和动画
                        attr {
                            if (cardData.isDragging) {
                                transform(
                                    scale = Scale(ctx.config.dragScaleRatio, ctx.config.dragScaleRatio),
                                    translate = Translate(
                                        percentageX = 0f,
                                        percentageY = 0f,
                                        offsetX = cardData.offsetX,
                                        offsetY = cardData.offsetY
                                    )
                                )
                                opacity(ctx.config.dragOpacity)
                            } else {
                                transform(
                                    rotate = Rotate(cardData.shakeAngle),
                                    translate = Translate(
                                        percentageX = 0f,
                                        percentageY = 0f,
                                        offsetX = cardData.offsetX,
                                        offsetY = cardData.offsetY
                                    )
                                )
                                if (cardData.needsAnimation) {
                                    animate(
                                        Animation.springEaseInOut(ctx.config.dragAnimationDuration, 1f, 0f),
                                        cardData.animationKey
                                    )
                                } else if (ctx.config.shakeEnabled) {
                                    // 读取 shakeKey 建立响应式依赖，确保角度变化时 attr 块重新求值
                                    val shakeKeyVal = cardData.shakeKey
                                    if (!ctx.pagerData.isIOS) {
                                        // 非 iOS：平滑抖动动画
                                        animate(Animation.easeInOut(ctx.config.shakeAnimationDuration), shakeKeyVal)
                                    }
                                    // iOS：不调用 animate()，角度直接跳变模拟抖动
                                    // 规避 iOS hitTest 在动画期间跳过子视图检测的 bug（KRView.m）
                                    // TODO: 框架升级修复 hitTest 后移除 isIOS 判断，统一使用 animate
                                }
                            }
                        }

                        // 卡片主体（带背景和圆角，向内缩进留出按钮空间）
                        View {
                            attr {
                                absolutePosition(top = ctx.topOverflow, left = ctx.leftOverflow, right = ctx.rightOverflow, bottom = 0f)
                                backgroundColor(ctx.config.cardBackgroundColor)
                                borderRadius(ctx.config.cardBorderRadius)
                                opacity(if (cardData.isTouching && !ctx.attr.editing) 0.7f else 1f)
                            }

                            // 业务自定义的卡片内容
                            ctx.attr._cardContentBuilder?.invoke(this, cardData)
                        }

                        // 编辑模式下的删除按钮（左上角）
                        vif({ ctx.attr.editing }) {
                            View {
                                attr {
                                    absolutePosition(top = ctx.topOverflow - ctx.leftOverflow, left = 0f)
                                    size(ctx.config.deleteButtonSize, ctx.config.deleteButtonSize)
                                    backgroundColor(ctx.config.deleteButtonColor)
                                    borderRadius(ctx.config.deleteButtonSize / 2)
                                    allCenter()
                                }
                                event {
                                    click {
                                        cardData.buttonClicked = true
                                        ctx.deleteCard(cardData)
                                    }
                                }
                                Text {
                                    attr {
                                        text("−")
                                        fontSize(18f)
                                        fontWeightBold()
                                        color(Color.WHITE)
                                    }
                                }
                            }
                        }

                        // 编辑模式下的尺寸切换按钮（右上角）
                        vif({ ctx.attr.editing && ctx.config.resizeEnabled }) {
                            View {
                                attr {
                                    absolutePosition(top = ctx.topOverflow - ctx.rightOverflow, right = 0f)
                                    size(ctx.config.resizeButtonSize, ctx.config.resizeButtonSize)
                                    backgroundColor(ctx.config.resizeButtonColor)
                                    borderRadius(ctx.config.resizeButtonSize / 2)
                                    allCenter()
                                }
                                event {
                                    click {
                                        cardData.buttonClicked = true
                                        val newSpan = if (cardData.spanX == 1) 2 else 1
                                        ctx.resizeCard(cardData, newSpan)
                                    }
                                }
                                Text {
                                    attr {
                                        text("↔")
                                        fontSize(14f)
                                        fontWeightBold()
                                        color(Color.WHITE)
                                    }
                                }
                            }
                        }

                        // 触摸和拖拽事件
                        event {
                            register(EventName.TOUCH_DOWN.value) {
                                cardData.isTouching = true
                                cardData.wasPanned = false
                                cardData.longPressFired = false
                                cardData.buttonClicked = false
                                if (!ctx.attr.editing) {
                                    cardData.longPressCallback = setTimeout(ctx.config.longPressDelay) {
                                        cardData.longPressFired = true
                                        cardData.longPressCallback = null
                                        ctx.onCardLongPress()
                                    }
                                }
                            }
                            register(EventName.TOUCH_UP.value) {
                                cardData.isTouching = false
                                cardData.longPressCallback?.let { callback ->
                                    clearTimeout(callback)
                                    cardData.longPressCallback = null
                                }
                                // 没有拖拽、没有触发长按、没有点击按钮 → 视为卡片点击
                                if (!cardData.wasPanned && !cardData.longPressFired && !cardData.buttonClicked) {
                                    ctx.event.fireCardClick(cardData)
                                }
                            }
                            pan { params ->
                                if (params.state == "start" || params.state == "move") {
                                    cardData.wasPanned = true
                                }
                                if (params.state == "start") {
                                    cardData.longPressCallback?.let { callback ->
                                        clearTimeout(callback)
                                        cardData.longPressCallback = null
                                    }
                                }
                                if (ctx.attr.editing) {
                                    val currentIndex = ctx.cardList.indexOf(cardData)
                                    if (currentIndex >= 0) {
                                        ctx.handleDrag(params, cardData, currentIndex)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun createAttr(): WidgetGridAttr = WidgetGridAttr()

    override fun createEvent(): WidgetGridEvent = WidgetGridEvent()
}

// ==================== 扩展函数 ====================

/**
 * 在 ViewContainer 中添加 WidgetGrid 组件
 *
 * 示例用法：
 * ```
 * WidgetGrid {
 *     attr {
 *         config = WidgetGridConfig(columnCount = 3)
 *         gridWidth = pagerData.pageViewWidth - 32f
 *         editing = isEditing
 *         cardContent { item -> ... }
 *     }
 *     event {
 *         onEditingChanged { editing -> isEditing = editing }
 *     }
 * }
 * ```
 */
fun ViewContainer<*, *>.WidgetGrid(init: WidgetGridView.() -> Unit) {
    addChild(WidgetGridView(), init)
}
