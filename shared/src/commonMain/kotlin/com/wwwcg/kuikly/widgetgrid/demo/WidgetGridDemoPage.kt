package com.wwwcg.kuikly.widgetgrid.demo

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.log.KLog
import com.wwwcg.kuikly.widgetgrid.WidgetGrid
import com.wwwcg.kuikly.widgetgrid.WidgetGridConfig
import com.wwwcg.kuikly.widgetgrid.WidgetGridItemData
import com.wwwcg.kuikly.widgetgrid.WidgetGridView
import com.wwwcg.kuikly.widgetgrid.base.BasePager

// ==================== 业务自定义卡片数据 ====================

/**
 * 继承 WidgetGridItemData，添加业务需要的自定义属性
 */
class DemoCardData(scope: PagerScope) : WidgetGridItemData(scope) {
    var title: String by observable("")
    var iconColor: Color by observable(Color.BLUE)
}

// ==================== Demo 页面 ====================

@Page("WidgetGridDemoPage")
internal class WidgetGridDemoPage : BasePager() {

    // 编辑状态
    var isEditing by observable(false)

    // WidgetGrid 引用
    lateinit var gridRef: ViewRef<WidgetGridView>

    // 用于生成新卡片 id
    private var nextId = 11

    // 网格配置
    private val gridConfig = WidgetGridConfig(
        columnCount = 3,
        cardHeight = 100f,
        cardSpacing = 12f,
        resizeEnabled = true,
    )

    private val horizontalPadding = 16f

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFF1C1C1EL))
            }

            // 顶部导航栏
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color(0xFF2C2C2EL))
                }
                View {
                    attr {
                        height(56f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(16f)
                        paddingRight(16f)
                    }

                    // 返回按钮
                    View {
                        attr {
                            size(32f, 32f)
                            allCenter()
                        }
                        event {
                            click {
                                ctx.getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                            }
                        }
                        Text {
                            attr {
                                text("←")
                                fontSize(20f)
                                color(Color.WHITE)
                            }
                        }
                    }

                    // 标题
                    Text {
                        attr {
                            flex(1f)
                            text("小组件")
                            fontSize(18f)
                            fontWeightBold()
                            color(Color.WHITE)
                            textAlignCenter()
                        }
                    }

                    // 编辑/完成按钮
                    View {
                        attr {
                            paddingLeft(12f)
                            paddingRight(12f)
                            paddingTop(6f)
                            paddingBottom(6f)
                            backgroundColor(if (ctx.isEditing) Color(0xFF0A84FFL) else Color(0xFF3A3A3CL))
                            borderRadius(16f)
                        }
                        event {
                            click {
                                ctx.isEditing = !ctx.isEditing
                                ctx.gridRef.view?.setEditing(ctx.isEditing)
                            }
                        }
                        Text {
                            attr {
                                text(if (ctx.isEditing) "完成" else "编辑")
                                fontSize(14f)
                                color(Color.WHITE)
                            }
                        }
                    }
                }
            }

            // 卡片网格区域
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(ctx.horizontalPadding)
                    paddingRight(ctx.horizontalPadding)
                    paddingTop(16f)
                    paddingBottom(100f)
                }

                // WidgetGrid 组件
                WidgetGrid {
                    ref {
                        ctx.gridRef = it
                    }

                    attr {
                        config = ctx.gridConfig
                        gridWidth = pagerData.pageViewWidth - ctx.horizontalPadding * 2
                        editing = ctx.isEditing

                        // 自定义卡片内容
                        cardContent { item ->
                            val cardData = item as DemoCardData
                            View {
                                attr {
                                    flex(1f)
                                    padding(12f)
                                }

                                // 图标
                                View {
                                    attr {
                                        size(40f, 40f)
                                        backgroundColor(cardData.iconColor)
                                        borderRadius(10f)
                                        marginBottom(8f)
                                    }
                                }

                                // 标题
                                Text {
                                    attr {
                                        text(cardData.title)
                                        fontSize(14f)
                                        fontWeightMedium()
                                        color(Color.WHITE)
                                    }
                                }

                                // 尺寸标签
                                Text {
                                    attr {
                                        marginTop(4f)
                                        text(if (cardData.spanX == 2) "2×1" else "1×1")
                                        fontSize(12f)
                                        color(Color(0xFF8E8E93L))
                                    }
                                }
                            }
                        }
                    }

                    event {
                        onEditingChanged { editing ->
                            ctx.isEditing = editing
                        }
                        onReorder { from, to ->
                            // 可在此处理排序后的业务逻辑（如持久化）
                        }
                        onDelete { item ->
                            // 可在此处理删除后的业务逻辑
                        }
                        onCardClick { item ->
                            // 非编辑态点击卡片的业务逻辑（如跳转详情页）
                            KLog.d("WidgetGridDemoPage", "onCardClick: ${item.id}")
                        }
                        onResize { item, oldSpanX, newSpanX ->
                            // 尺寸切换后更新卡片内容（如 1x1 → 2x1 显示更多信息）
                            KLog.d("WidgetGridDemoPage", "onResize: ${item.id}, oldSpanX: $oldSpanX, newSpanX: $newSpanX")
                            val card = item as DemoCardData
                            card.title = if (newSpanX == 2) "${card.title} ⬛" else card.title.removeSuffix(" ⬛")
                        }
                    }
                }
            }

            // 底部添加按钮
            vif({ ctx.isEditing }) {
                View {
                    attr {
                        absolutePosition(bottom = 40f + ctx.pagerData.safeAreaInsets.bottom)
                        alignSelfCenter()
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(20f)
                        paddingRight(20f)
                        paddingTop(12f)
                        paddingBottom(12f)
                        backgroundColor(Color(0xFF0A84FFL))
                        borderRadius(24f)
                    }
                    event {
                        click {
                            ctx.addNewCard()
                        }
                    }
                    Text {
                        attr {
                            text("+ 添加小组件")
                            fontSize(16f)
                            fontWeightMedium()
                            color(Color.WHITE)
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()

        // 初始化测试数据
        val testData = listOf(
            Triple(1, 1, "天气"),
            Triple(2, 2, "日历"),
            Triple(3, 1, "时钟"),
            Triple(4, 1, "备忘录"),
            Triple(5, 2, "音乐"),
            Triple(6, 1, "健康"),
            Triple(7, 1, "相册"),
            Triple(8, 1, "快捷指令"),
            Triple(9, 2, "屏幕使用时间"),
            Triple(10, 1, "电池"),
        )

        val items = testData.map { (id, span, title) ->
            DemoCardData(this).apply {
                this.id = id
                this.spanX = span
                this.title = title
                this.iconColor = Color(
                    (100..255).random(),
                    (100..255).random(),
                    (100..255).random(),
                    1.0f
                )
            }
        }

        gridRef.view?.addItems(items)
    }

    private fun addNewCard() {
        val newId = nextId++
        val spanX = if (newId % 3 == 0) 2 else 1
        gridRef.view?.addItem(DemoCardData(this).apply {
            id = newId
            this.spanX = spanX
            title = "新组件 $newId"
            iconColor = Color(
                (100..255).random(),
                (100..255).random(),
                (100..255).random(),
                1.0f
            )
        })
    }
}
