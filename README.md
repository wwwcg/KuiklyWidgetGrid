# KuiklyWidgetGrid

一个基于 [KuiklyUI](https://github.com/Tencent-TDS/KuiklyUI) 的卡片式拖动排序组件，类似 iPhone 负一屏的小组件管理体验。

## ✨ 功能特性

- **多尺寸卡片** — 支持 1×1 和 2×1 两种网格占位
- **拖拽排序** — 编辑模式下拖动卡片，其他卡片自动"推挤"让位，松手后自动归位
- **智能换行** — 2×1 卡片在目标行放不下时自动移到下一行
- **编辑模式** — 长按进入编辑态，卡片抖动提示可编辑
- **删除卡片** — 编辑态下点击删除按钮移除卡片（iOS 带位置过渡动画）
- **自定义内容** — 卡片外观完全由业务方自定义，组件只负责布局和交互
- **高度可配** — 列数、间距、高度、动画参数、抖动效果等均可配置
- **跨平台** — 基于 Kotlin Multiplatform，支持 Android、iOS、HarmonyOS、macOS、Web(JS)

## 📦 项目结构

```
KuiklyWidgetGrid/
├── widgetgrid/                    # 📦 组件库模块（可独立发布到 Maven）
│   ├── build.gradle.kts
│   └── src/commonMain/kotlin/com/wwwcg/kuikly/widgetgrid/
│       ├── WidgetGridConfig.kt    # 网格配置
│       ├── WidgetGridItemData.kt  # 卡片数据基类
│       └── WidgetGrid.kt         # 主组件 + 扩展函数
│
├── shared/                        # 📱 Demo 模块（使用示例）
│   └── src/commonMain/kotlin/.../demo/
│       └── WidgetGridDemoPage.kt  # 完整使用示例
│
├── androidApp/                    # Android 宿主应用
├── iosApp/                        # iOS 宿主应用
└── settings.gradle.kts
```

## 🚀 快速接入

### 1. 添加依赖

**Maven Central（推荐）：**

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.wwwcg:widgetgrid:1.0.0")
            }
        }
    }
}
```

**本地模块依赖（开发阶段）：**

在 `settings.gradle.kts` 中：

```kotlin
include(":widgetgrid")
```

在业务模块的 `build.gradle.kts` 中：

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":widgetgrid"))
            }
        }
    }
}
```

### 2. 定义卡片数据

继承 `WidgetGridItemData`，添加业务需要的自定义属性：

```kotlin
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.handler.observable
import com.wwwcg.kuikly.widgetgrid.WidgetGridItemData

class MyCardData(scope: PagerScope) : WidgetGridItemData(scope) {
    var title: String by observable("")
    var subtitle: String by observable("")
    var iconColor: Color by observable(Color.BLUE)
}
```

> **注意：** 自定义属性建议使用 `by observable()` 委托，以支持 Kuikly 的响应式更新机制。

### 3. 在页面中使用 WidgetGrid

```kotlin
import com.wwwcg.kuikly.widgetgrid.*

@Page("MyWidgetPage")
class MyWidgetPage : BasePager() {

    // 编辑状态（响应式）
    var isEditing by observable(false)

    // WidgetGrid 视图引用，用于调用 addItem 等方法
    lateinit var gridRef: ViewRef<WidgetGridView>

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFF1C1C1EL))
            }

            // ---- 1. 导航栏（包含编辑/完成按钮） ----
            View {
                attr { /* 你的导航栏样式 */ }
                // 编辑/完成按钮
                View {
                    event {
                        click {
                            ctx.isEditing = !ctx.isEditing
                            ctx.gridRef.view?.setEditing(ctx.isEditing)
                        }
                    }
                    Text {
                        attr { text(if (ctx.isEditing) "完成" else "编辑") }
                    }
                }
            }

            // ---- 2. WidgetGrid 组件 ----
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(16f)
                    paddingRight(16f)
                    paddingTop(16f)
                }

                WidgetGrid {
                    // 获取视图引用
                    ref { ctx.gridRef = it }

                    attr {
                        // 网格配置
                        config = WidgetGridConfig(
                            columnCount = 3,
                            cardHeight = 100f,
                            cardSpacing = 12f,
                        )
                        // 网格可用宽度 = 页面宽度 - 左右 padding
                        gridWidth = pagerData.pageViewWidth - 32f
                        // 绑定编辑状态（响应式）
                        editing = ctx.isEditing

                        // 自定义卡片内容
                        cardContent { item ->
                            val card = item as MyCardData
                            View {
                                attr { flex(1f); padding(12f) }
                                Text {
                                    attr {
                                        text(card.title)
                                        fontSize(14f)
                                        color(Color.WHITE)
                                    }
                                }
                            }
                        }
                    }

                    event {
                        // 编辑态变化（如长按触发进入编辑）
                        onEditingChanged { editing ->
                            ctx.isEditing = editing
                        }
                        // 卡片重新排序完成
                        onReorder { fromIndex, toIndex ->
                            // 持久化新顺序
                        }
                        // 卡片被删除
                        onDelete { item ->
                            // 处理删除后的业务逻辑
                        }
                    }
                }
            }

            // ---- 3. 添加按钮（编辑态显示） ----
            vif({ ctx.isEditing }) {
                View {
                    event {
                        click { ctx.addNewCard() }
                    }
                    Text { attr { text("+ 添加小组件") } }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        // 初始化卡片数据
        val items = listOf(
            MyCardData(this).apply { id = 1; spanX = 1; title = "天气" },
            MyCardData(this).apply { id = 2; spanX = 2; title = "日历" },
            MyCardData(this).apply { id = 3; spanX = 1; title = "时钟" },
        )
        gridRef.view?.addItems(items)
    }

    private fun addNewCard() {
        gridRef.view?.addItem(MyCardData(this).apply {
            id = System.currentTimeMillis().toInt()
            spanX = 1
            title = "新组件"
        })
    }
}
```

## 📖 API 参考

### WidgetGridConfig — 网格配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `columnCount` | `Int` | `3` | 列数 |
| `cardHeight` | `Float` | `100f` | 卡片高度（dp） |
| `cardSpacing` | `Float` | `12f` | 卡片间距（dp） |
| `dragScaleRatio` | `Float` | `1.05f` | 拖拽时卡片放大比例 |
| `dragOpacity` | `Float` | `0.9f` | 拖拽时卡片透明度 |
| `dragAnimationDuration` | `Float` | `0.3f` | 拖拽时其他卡片位移动画时长（秒） |
| `shakeEnabled` | `Boolean` | `true` | 是否启用编辑态抖动效果 |
| `shakeInterval` | `Int` | `200` | 抖动切换间隔（毫秒） |
| `shakeAngleBase` | `Float` | `1.2f` | 基础抖动角度（度） |
| `shakeAngleOffset` | `Float` | `0.5f` | 相邻卡片角度偏移，让抖动更自然 |
| `shakeAnimationDuration` | `Float` | `0.2f` | 抖动动画时长（秒） |
| `longPressDelay` | `Int` | `350` | 长按触发延迟（毫秒） |
| `cardBackgroundColor` | `Color` | `0xFF2C2C2E` | 卡片默认背景色 |
| `cardBorderRadius` | `Float` | `16f` | 卡片圆角半径（dp） |
| `deleteButtonSize` | `Float` | `24f` | 删除按钮尺寸（dp） |
| `deleteButtonOffset` | `Float` | `-8f` | 删除按钮相对左上角偏移（dp），负值向外延伸 |
| `deleteButtonColor` | `Color` | `0xFFFF3B30` | 删除按钮背景色 |

### WidgetGridItemData — 卡片数据基类

| 属性 | 类型 | 说明 |
|------|------|------|
| `id` | `Int` | 卡片唯一标识 |
| `spanX` | `Int` | 横向占位格数（`1` = 1×1，`2` = 2×1） |

> 继承此类添加业务自定义属性，使用 `by observable()` 委托支持响应式更新。

### WidgetGridAttr — 组件属性

| 属性/方法 | 类型 | 说明 |
|-----------|------|------|
| `config` | `WidgetGridConfig` | 网格配置 |
| `editing` | `Boolean` | 编辑模式开关（响应式，外部控制） |
| `gridWidth` | `Float` | 网格可用宽度（dp） |
| `cardContent { }` | 函数 | 卡片内容构建器，接收 `WidgetGridItemData` 参数 |

### WidgetGridEvent — 组件事件

| 事件 | 参数 | 说明 |
|------|------|------|
| `onEditingChanged` | `(Boolean) -> Unit` | 编辑状态变化（如长按触发进入编辑态） |
| `onReorder` | `(fromIndex: Int, toIndex: Int) -> Unit` | 卡片拖拽排序完成 |
| `onDelete` | `(WidgetGridItemData) -> Unit` | 卡片被删除 |

### WidgetGridView — 视图方法（通过 ViewRef 调用）

| 方法 | 说明 |
|------|------|
| `addItem(item)` | 添加单个卡片 |
| `addItems(items)` | 批量添加卡片 |
| `removeItem(id)` | 根据 id 移除卡片（带动画） |
| `getItems()` | 获取当前卡片列表的副本 |
| `setEditing(editing)` | 设置编辑状态（同时触发 `onEditingChanged` 事件） |

## 🔧 进阶用法

### 自定义网格布局

```kotlin
// 2 列布局，大间距，高卡片
config = WidgetGridConfig(
    columnCount = 2,
    cardHeight = 150f,
    cardSpacing = 20f,
)
```

### 自定义卡片样式

```kotlin
config = WidgetGridConfig(
    cardBackgroundColor = Color.WHITE,
    cardBorderRadius = 24f,
    deleteButtonColor = Color(0xFFE53935L),
    deleteButtonSize = 28f,
    deleteButtonOffset = -10f,
)
```

### 关闭抖动效果

```kotlin
config = WidgetGridConfig(
    shakeEnabled = false,
)
```

### 调整拖拽手感

```kotlin
config = WidgetGridConfig(
    dragScaleRatio = 1.1f,       // 拖拽时放大更多
    dragOpacity = 0.8f,          // 更透明
    dragAnimationDuration = 0.5f, // 其他卡片移动更慢
    longPressDelay = 500,        // 长按更久才触发
)
```

### 在 cardContent 中使用条件渲染

```kotlin
cardContent { item ->
    val card = item as MyCardData

    // 响应式属性变化 → 在 attr 块中读取
    Text {
        attr {
            text(card.title) // card.title 变化时自动更新
            color(Color.WHITE)
        }
    }

    // 条件渲染 → 使用 vif 指令
    vif({ (item as MyCardData).showBadge }) {
        View {
            attr {
                size(8f, 8f)
                backgroundColor(Color.RED)
                borderRadius(4f)
            }
        }
    }
}
```

## ⚠️ 平台差异说明

| 行为 | iOS | Android |
|------|-----|---------|
| 删除动画 | ✅ 其他卡片平滑过渡到新位置 | ⚡ 直接删除，无位置过渡动画 |
| 拖拽动画 | ✅ 弹性动画（springEaseInOut） | ✅ 弹性动画（springEaseInOut） |
| 抖动动画 | ✅ 正常 | ✅ 正常 |

> Android 删除时不使用位置过渡动画，是为了避免抖动动画与位移动画在该平台上的冲突。

## 📋 注意事项

1. **卡片数据的 PagerScope**：创建 `WidgetGridItemData`（或其子类）时，需传入 `PagerScope`（通常是 Pager 的 `this`）。

2. **添加卡片的时机**：通过 `gridRef.view?.addItems(...)` 添加卡片，需要在 `viewDidLoad()` 或之后调用，确保 `gridRef` 已绑定。

3. **编辑状态同步**：组件的编辑状态有两种控制方式：
   - **外部控制**：通过 `attr { editing = ... }` 响应式绑定
   - **方法调用**：通过 `gridRef.view?.setEditing(true/false)` 命令式调用

   当用户长按进入编辑态时，组件会触发 `onEditingChanged(true)` 事件，业务方需在此回调中同步自己的状态。

4. **gridWidth**：必须设置 `gridWidth`，组件需要此值计算卡片宽度和布局。通常为 `pagerData.pageViewWidth - 左右 padding`。

5. **Scroller 包裹**：`WidgetGrid` 本身不包含滚动容器，需要业务方用 `Scroller` 包裹，以支持内容超出屏幕时滚动。

## 📄 License

MIT License
