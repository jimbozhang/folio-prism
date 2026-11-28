# folio-prism 设计文档

- 状态：已实现，按当前实现维护
- 版本：0.4
- 日期：2026-11-24
- 关联文档：[需求文档](REQUIREMENTS.md)

本文档描述实现方案。需求层面的「为什么」见 `REQUIREMENTS.md`；本文档聚焦「怎么做」。2026-11-20 按当前代码同步修订：更新依赖与包结构、事件模型、价格缓存结构、Web 工作台、首页范围条与黄金阈值价、交易筛选等内容。2026-11-24（0.4）再次同步：调仓统计替换费用统计、资产曲线归因与剔除资金流曲线、历史缓存新鲜度判定、单价建议优先级、记账页键盘与选择面板、无持仓首页、流水记录时间字段移除。

---

## 1. 技术选型

| 项 | 选择 | 说明 |
|---|---|---|
| 语言 | Kotlin | Google 官方 Android 开发语言；纯 Kotlin 计算核心可在 JVM 单测 |
| UI | Jetpack Compose + Material 3 | 原生 Android，适合仪表盘/图表；不做 WebView |
| 导航 | Navigation Compose | 单 Activity + Composable 页面 |
| 异步 | Kotlin Coroutines + Flow | 行情并发、启动门禁、状态流 |
| JSON | kotlinx.serialization | 纯文本、schemaVersion、BigDecimal 字符串序列化 |
| 网络 | OkHttp 4.12 | 直接调用公开行情接口；不用 Retrofit 以减少抽象 |
| 图表 | Compose Canvas / 布局组件 | 折线、柱状、横向条形均自绘；不引入第三方图表库 |
| Web 工作台 | NanoHTTPD 2.3.1 + 内置静态资源 + Chart.js | 手机内置 HTTP 服务，浏览器访问；静态资源随 APK 打包，不依赖外网 |
| 存储 | 固定目录 `Documents/folio-prism/` 下的单个 JSON 文件 | 不使用 SQLite/Room；数据规模小，纯文本可读、易备份 |
| 存储权限 | `MANAGE_EXTERNAL_STORAGE` | Android 11+ 直接读写公共 Documents 目录；不提供目录选择 |
| 依赖注入 | 手工组合根 | App 规模小，不引入 Hilt/Koin |
| 构建 | Gradle Wrapper + Android SDK cmdline-tools | JDK 17；debug APK；自用不上架 |
| minSdk | 26 | 覆盖两台目标机；Redmi 14C 出厂 Android 14 |
| 显示名 | 资产配置 | 已确认 |
| applicationId | `com.jimbozhang.folioprism` | 已确认 |

**不引入**：Room、Retrofit、Hilt、WebView、Flutter、React Native、SAF 目录选择、第三方图表库（Vico 等）。

### 1.1 工程结构

单 Gradle module（`:app`），按包分层：

```
app/src/main/java/com/jimbozhang/folioprism/
  domain/         纯 Kotlin，无 Android 依赖
    model/        实体与事件
    calc/         估值、再平衡、事件回放、盈亏、转账建议、区间条窗口
    history/      区间预设与历史结果模型
    quote/        行情路由
    money/        Decimal 工具
  data/
    json/         序列化与原子写入
    store/        PortfolioRepository（内存状态 + 持久化）、BackupManager
    price/        QuoteService、HttpFetcher（OkHttp）
    history/      HistoricalPriceService、PriceCacheRepository
    web/          WebDataService、WebDataServer、WebPortfolioApi、WebStats 等
  platform/
    storage/      固定目录、MANAGE_EXTERNAL_STORAGE 检查
  ui/
    home/ charts/ transactions/ settings/ debug/
    components/   复用组件（区间条、金额格式化、选择面板等）
    theme/
  MainActivity.kt
  FolioPrismApplication.kt
app/src/main/assets/web/   Web 工作台静态资源（HTML/CSS/JS/Chart.js）
```

`domain` 包必须能作为纯 JVM 代码测试；Android 相关能力通过接口注入。

---

## 2. 总体架构

### 2.1 核心对象

- `AppViewModel`：UI 状态持有者。暴露 `AppUiState`（Loading / LoadingQuotes / Ready / QuoteFailed / Error），负责加载数据、抓取行情、加载历史。
- `PortfolioRepository`：唯一数据源。持有内存中的 `PortfolioData`，所有修改通过 `update {}` 完成，保存前备份并原子写入；文件缺失时创建默认空数据。
- `SnapshotCalculator`：纯函数。由持仓 + 价格/汇率计算类别市值、双口径、再平衡金额、视觉占比、黄金阈值价、总览缩放。
- `HistoryCalculator`：纯函数。按日回放事件并估值，计算曲线、类别资产、类别盈亏、资金流与缺口。
- `QuoteService`：当前行情。按「标的 → 首选数据源」抓取，返回 `QuoteSnapshot` 与失败列表；启动门禁由失败列表驱动。
- `HistoricalPriceService`：历史行情。按区间抓取、合并缓存、失败退避。
- `PriceCacheRepository`：历史价格缓存文件（独立 JSON）。
- `BackupManager`：主数据写入前的滚动备份。
- `TransferSuggestion`：转账数量/价格建议。
- `WebDataService` / `WebDataServer` / `WebPortfolioApi`：局域网 Web 工作台（前台服务 + NanoHTTPD + API）。

### 2.2 启动流程

```
启动
  → MainActivity 检查「所有文件访问」权限（onCreate + onResume）
      → 未授予：显示权限说明页，跳转系统授权页
  → AppViewModel.load()
      → PortfolioRepository.load()
          → 文件缺失：创建默认空数据（预置 7 类规则）并写入
          → 文件损坏：显示全屏「加载失败」页（可重试）
      → 状态 LoadingQuotes，QuoteService.fetch() 抓取必需当前价格 + 汇率（仅首选源）
          → 无失败：Ready → 首页
          → 有失败：QuoteFailed → 全屏调试页（行情失败页）
```

启动期间显示加载页（「正在加载数据…」「正在获取行情…」），不展示总资产。**任何必需价格失败都不进入主界面**。价格缓存不参与门禁判断；当前价格不写入历史缓存。

### 2.3 刷新流程

- 首页与图表页均使用 `PullToRefreshBox`，触发 `AppViewModel.refreshQuotes()`。
- 成功：更新内存报价、重算快照；图表页按价格指纹重建当前区间历史。
- 失败：进入全屏调试页（行情失败页），直到刷新成功；不得用上一次的内存价格继续展示总资产（Q-04）。
- 不实现后台定时刷新；交易页与设置页没有下拉刷新。

### 2.4 状态与并发

- 行情请求：汇率单请求 → 手工价本地取值 → 基金一次批量请求 → ETF/美股按标的使用 `async` 并发（无并发上限）→ 黄金按源请求一次。HTTP 超时固定为连接/读取 8s、整体 12s；每个请求最多重试 1 次（间隔 500ms）。`Settings.quote.timeoutMs/retryCount/maxConcurrency` 目前在代码中未接线。
- 同一标的被多个机构持有时只请求一次（按 instrumentId 去重）。
- `PortfolioRepository.update` 是「读当前内存 → transform → 保存」的读改写，**没有加锁**；App 与 Web 工作台并发写事件时存在丢失更新风险（已知限制）。
- Web 工作台的行情/历史缓存分别用 `Mutex` 串行化；HTTP 请求在 NanoHTTPD 的独立线程中 `runBlocking` 处理。
- UI 状态通过 `StateFlow` 暴露；Composable 只订阅状态，不直接做计算。

---

## 3. 数据模型

### 3.1 主数据文件 `folio-prism.json`

（省略）

### 3.2 实体定义

#### Category（7 个战略类别 + 可选卫星类别）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 战略类别为固定 ID，不可增删；卫星类别由用户在 JSON 中自行添加 |
| name | string | 内置名称（红利/成长/美股/美债/中债/黄金/现金/卫星），不可编辑；JSON 中的值在加载时被规范化，仅对未知类别保留原值 |
| order | int | 展示顺序 |
| color | string | 图表颜色 |
| targetPct | decimal string | 目标比例（百分数，如 `"32"`）；卫星类别填 `"0"`，不参与计算 |
| minPct / maxPct | decimal string | 允许区间；卫星类别填 `"0"`，不参与计算 |
| specialRule | `"dividend"` \| `"satellite"` \| null | 红利类使用策略资产基数；卫星类计入总资产但不参与再平衡；其余为 null |

卫星类别（`specialRule = "satellite"`，可选、至多一个即可）的语义：

- 市值计入总资产与历史曲线，并出现在类别资产、类别盈亏、机构/币种分布与当前位置表中；
- 不设置目标/区间，不参与再平衡：七类的目标基数（策略资产 = 总资产 − 卫星）与黄金阈值基数均剔除卫星，调仓建议与调仓统计忽略任何涉及卫星的转账；
- 首页不进入「资产配置」总览，也不画范围条；仅在七张类别卡片之后显示一张紧凑卡片（名称 + 当前金额 + 占总资产比例 + 可展开持仓）；
- 卫星资产不写入默认数据：新增/编辑标的时类别下拉始终提供「卫星」，首次保存自动创建类别（也可手工写入 `settings.categories`）。

默认规则（仅当主 JSON 缺失时用于新建文件；JSON 存在时其 `settings.categories` 完全覆盖默认值）：

- 红利：目标 32%（对总资产），区间 28.8%–35.2%；
- 其余 6 类：目标各为去除红利后基数的 1/6（≈16.67%），区间为标准值的 ±10%（15%–18.33%）。

类别名称为内置常量，UI 不提供改名入口；类别颜色与顺序定义在 JSON 中；战略类别的 UI 只允许编辑目标与最低/最高比例，卫星类别为只读行。

#### Institution（机构）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | UUID/稳定 ID |
| name | string | 交通银行、中信证券等 |
| type | `bank` \| `broker` \| `other` | 影响文案/图标，不影响计算 |
| chartAlias | string? | 图表显示别名，如 人民银行 → 熊猫金币 |
| notes | string | 备注 |

#### Instrument（标的）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 稳定 ID |
| categoryId | string | 已配置类别之一（7 个战略类别，若已配置则含卫星）；用户可自由分配（如 000015 → 现金） |
| code | string | 008163、VOO、AU9999、CNY、USD 等 |
| name | string | 显示名称 |
| currency | string | `CNY` / `USD` / `HKD` / `CNH` |
| unit | string | 份/股/克/元/张 |
| kind | `cash` \| `quoted` | 现金类（数量即金额）与有净值类 |
| quote.mode | `auto` \| `manual` \| `none` | `none` 通常用于现金；对 quoted 标的没有特殊短路，仍按 source/代码推断路由 |
| quote.source | string | `auto` 或显式适配器名（`manual`、`fund`/`eastmoneyFund`、`etf`/`tencentEtf`、`us`/`tencentUs`、`au9999`/`sge`、`xau`）；未识别的值会记为失败 |
| quote.manualPrice | decimal string? | 手工当前价格 |
| precision.priceDecimals | int | 单价显示/保存精度 |
| precision.quantityDecimals | int | 数量/份额精度 |
| currencyExposure | `auto` \| `CNY` \| `USD` \| `HKD` \| `PHYSICAL_GOLD` | 币种分布图覆盖；默认 auto |
| notes | string | 备注 |

**现金标的**：`kind=cash`。数量字段表示原币金额；估值 `valueCNY = quantity × fx(currency→CNY)`；CNY 的 fx=1；CNH 在 v1 视作 CNY（fx=1）。现金标的可以是 `CNY`、`USD`、`HKD`、`CNH`，也可以是现金类产品（活钱宝、美元定期），只要 `kind=cash` 且 `quote.mode=none` 或 `manual`。

**有净值标的**：`kind=quoted`。估值 `valueCNY = quantity × price × fx(currency→CNY)`。现金类基金（如 000015）也使用 `kind=quoted`，但类别可设为现金，因此仍实时取净值。

#### Position（持仓，派生）

- 主键：`(institutionId, instrumentId)`。
- `quantity`：有净值标的为份额/股数；现金标的为原币金额。
- 持仓不由用户通过 UI 直接编辑，由事件回放得到；用户可在 App 关闭时手工编辑 JSON。
- 同一标的可在多个机构分别持有；标的元数据共享。

#### Event（事件）

所有事件公共字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 稳定唯一 ID |
| type | `transfer` \| `income` \| `expense` \| `external` \| `opening` \| `adjustment` | 事件类型 |
| date | ISO 8601 string | 业务发生时间（带时区） |
| createdAt | ISO 8601 string | 遗留字段：记录写入时间，已不再写入。保留仅为读取旧文件，加载后清空，下次保存即从文件中移除 |
| note | string | 备注 |

#### transfer（转账）

买入、卖出、基金转换、跨机构搬仓、现金换汇在账本上统一为转账。UI 可提供快捷预设，但事件类型相同。

| 字段 | 说明 |
|---|---|
| from / to | `{ institutionId, instrumentId }`，两个持仓 |
| fromQuantity | 转出数量：现金标的为金额（原币），有净值标的为份额/股数 |
| toQuantity | 转入数量：现金标的为金额（原币），有净值标的为份额/股数 |
| fromPrice | 转出标的单价（有净值标的必填；现金标的为 null） |
| toPrice | 转入标的单价（有净值标的必填；现金标的为 null） |
| lossAmount | 嵌入的手续费/损耗，原币；可为正（损耗）或负（收益） |
| lossCurrency | 损耗币种 |
| feePosition / feeAmount / feeCurrency | 可选：从独立现金账户支付的手续费 |
| note | 备注 |

计算：

```
fromNativeValue = fromQuantity × (fromPrice ?: 1)   // 现金标的 price 视为 1
toNativeValue   = toQuantity   × (toPrice ?: 1)
```

- 同币种转账默认 `lossAmount = fromNativeValue − toNativeValue`。跨币种转账：若两端都能精确折算为人民币（如人民币现金 + 按人民币输入的港币标的单价），自动计算人民币损耗并按汇率折算成转出币种存储；否则（如按原币输入外币单价）不自动计算，由用户手工输入，避免用当日汇率扭曲历史交易的损耗。
- `lossAmount` 是嵌入的手续费/损耗，不自动生成 `expense` 事件；类别盈亏直接按 `lossAmount` 扣减。
- 独立现金手续费记在 `feePosition`/`feeAmount`/`feeCurrency`，回放时从该现金账户扣除。
- 同一标的跨机构搬仓：两侧都是 quoted，`fromQuantity=toQuantity`、`fromPrice=toPrice`，`lossAmount=0`。
- 基金转换：拆成卖出 + 买入两笔转账（允许同一天），不设单独事件。

#### income（收益）

| 字段 | 说明 |
|---|---|
| kind | `dividend` / `interest` |
| source | 来源持仓；股息必填，利息可为 null |
| destination | 入账现金标的持仓（必须是 `kind=cash`） |
| amount | 收益金额（原币） |
| currency | 收益币种 |
| immediateExternalWithdrawal | 是否立即转出到外部消费账户 |
| externalAccountLabel | 外部账户标签（如「日常消费账户」） |
| linkedEventId | 若立即转出，关联的外部出金事件 ID |

语义：

- 分红全部为现金分红：现金增加，源持仓数量不变；净值下跌由行情体现。
- 不支持再投资；再投资由用户手工录入一笔买入转账。
- 利息：source 可为空（银行利息）；destination 为现金标的。
- `immediateExternalWithdrawal=true` 时，App 生成一笔关联的外部出金（`external`，`direction=out`，`classification=consumption`），金额等于到账金额；双向 `linkedEventId`。
- 分红/利息不减少源持仓数量。
- 事件只保存原币金额，不写汇率或 CNY 金额。

#### expense（支出）

| 字段 | 说明 |
|---|---|
| position | 可选；若为现金标的持仓，则真实扣减该现金余额；若为 null，表示转账损耗等非现金支出 |
| amount | 支出金额（原币），**可以为负值**（返还/收益） |
| currency | 币种 |
| categoryId | 盈亏归属类别（手续费/损耗计入哪个类别） |
| linkedTransferId | 旧数据中由转账差额生成的支出所关联的转账 ID；当前版本不再自动生成此类支出 |
| note | 备注 |

语义：

- `position != null`：现金减少（amount 为负则增加）；该现金变化已计入 ΔV。若 `categoryId` 不是现金类别，则在类别盈亏中把该金额从现金类别转移到 `categoryId` 对应类别（总盈亏不变）。
- `position == null`：不影响持仓；仅作为非现金损耗/收益记录，按 `categoryId` 直接扣减/增加该类别盈亏。
- 支出不属于资金流；它是投资成本/收益的一部分。
- 常见用途：投资手续费（position=现金）、转账损耗（position=null、linkedTransferId=转账）。

#### external（外部入金/出金）

| 字段 | 说明 |
|---|---|
| direction | `in` / `out` |
| classification | `capital` / `consumption` |
| position | 现金标的持仓（机构 × 现金标的） |
| amount / currency | 金额与原币 |
| counterpartyLabel | 外部账户标签，如「日常消费账户」「工资卡」 |
| note | 备注 |

语义（影响盈亏）：

- `in + capital`：外部本金投入，属于资金流，不计入盈亏。
- `out + capital` / `out + consumption`：本金取出/消费，属于资金流，不计入盈亏。
- 外部利息不再用 external + income；改用 `income`（source=null）。
- 外部手续费不再用 external + expense；改用 `expense`（position=现金）。

#### opening（期初）

| 字段 | 说明 |
|---|---|
| position | 机构 × 标的 |
| quantity | 有净值标的的期初份额 |
| amount | 现金标的的期初金额（与 quantity 二选一，按标的 kind 决定） |
| price | 有净值标的的期初单价（可选快照） |
| date | 期初时点 |

- 期初事件本身不产生盈亏，也不产生资金流；它定义回放的起点。
- 同一持仓只允许一条期初事件：编辑器与 Web 端保存第二笔时都会拒绝，并提示已有期初事件的日期。

#### adjustment（调整/平账）

| 字段 | 说明 |
|---|---|
| position | 机构 × 标的 |
| deltaQuantity | 有净值标的的数量增减（可为负） |
| deltaAmount | 现金标的的金额增减（可为负） |
| price | 事件日价格快照（可选） |
| reason | 原因（必填） |
| date | 时间 |

- 用于数据对账修正；**按资金流处理，不计入盈亏**，避免修正产生虚假收益。
- 允许出现负持仓/负现金，不阻止、不提示（D-25）；计算引擎需能处理负值。

#### 事件排序与回放

- 排序键：`date` → `id`（同一时刻按 id 稳定排序；不再使用记录时间）。App 流水列表、回放与 Web 流水排序一致。
- 回放范围：从最早事件到目标日期（含当日）。
- 持仓状态 = 期初 + 转账 + 收益 + 支出 + 外部 + 调整的累积。
- 允许负持仓/负现金；UI 不阻止；计算按负值继续。
- 删除/编辑事件后重新回放；1 万条事件规模需保持可用（见 NFR-03）。

### 3.3 历史价格缓存 `price-cache.json`

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-11-19T10:30:00+08:00",
  "quotes": {
    "ins_008163": {
      "auto": {
        "2026-01-02": "0.9810",
        "2026-01-05": "0.9852"
      },
      "manual": {},
      "lastAutoFetchAt": "2026-11-19T10:30:00+08:00",
      "source": "eastmoney pingzhongdata",
      "exhaustedUntilMs": "1764000000000",
      "lastFailedAtMs": "1763913600000"
    }
  }
}
```

- **没有 `fx` 段，也没有 `currency` 字段**：不抓取、不缓存历史汇率；跨币种历史估值统一用当前汇率。
- 读取：同一天 `manual` > `auto`；当天都没有时向过去最多 30 天取最近收盘价。
- `manual` 只读：当前版本没有手工补录入口，也没有 API 写入。
- 写入：历史行情抓取成功后合并 `auto` 并保存；当前行情刷新不写缓存（FR-START-09）。
- `lastFailedAtMs` / `exhaustedUntilMs`：历史抓取的失败时间与退避到期时间，跨重启保留。
- 缓存损坏时读取视为空缓存并重新抓取；缓存保存失败会让历史页进入错误态（不是静默警告）。
- 缓存文件与主数据同目录，用户可用文件管理器备份；App 内不显示缓存路径/更新时间。

---

## 4. 计算引擎

`domain` 包内全部为纯函数，输入 `PortfolioData` + `PriceProvider` + 时间点，输出计算结果。

### 4.1 估值

```
valueCNY(position @ t):
  instrument = instruments[position.instrumentId]
  if instrument.kind == cash:
      return position.quantity * fx(instrument.currency -> CNY, t)
  else:
      price = priceProvider.price(instrument, t)
      if instrument.code == "AU9999" (or gold source):
          price = goldPriceCNYPerGram(t) + premium(institutionId)
      return position.quantity * price * fx(priceCurrency -> CNY, t)
```

- CNY 的 fx = 1；CNH v1 fx = 1。
- 黄金加价按「机构」配置；`premium` 只对黄金类标的生效。
- 明细展示使用原币：`nativeValue = quantity × (price ?: 1)`；CNY 只用于汇总。
- 美元计价资产：只显示 USD 价值，不显示 CNY 等值；人民币计价资产：只显示 CNY，不显示 USD 等值。
- 类别汇总：CNY 口径；若类别内存在美元计价资产，额外显示美元资产小计（原币），不附 CNY 等值，也不把整个类别折算为 USD。
- 价格或当前汇率缺失时，`valueCNY = null`（历史统计中标记缺口）；当前估值缺失则启动门禁失败。

### 4.2 类别聚合与双口径

```
totalValue = Σ valueCNY(all positions)
dividendValue = Σ valueCNY(positions whose instrument.categoryId == cat_dividend)
portfolioValue = totalValue - dividendValue
```

- 红利：
  - `pct = dividendValue / totalValue`
  - `targetAmount = dynAmount(targetPct, totalValue, dividendValue)`
  - `minAmount = dynAmount(minPct, totalValue, dividendValue)`
  - `maxAmount = dynAmount(maxPct, totalValue, dividendValue)`
- 非红利类别：
  - `pct = catValue / portfolioValue`
  - `targetAmount = dynAmount(targetPct, portfolioValue, catValue)`
  - 同理 min/max。
- 分配总览图：
  - 视觉宽度统一以 `totalValue` 为分母；
  - `visualShare = catValue / totalValue`；
  - `visualTarget = targetPct × catBase / totalValue`，其中 `catBase` 红利为 `totalValue`、其他为 `portfolioValue`；
  - min/max 同理；
  - 全图共用一条缩放：`scale = min(max(0.1, 所有类别的 max(visualShare, visualTarget)) × 1.15, 1.0)`；min/max 刻度不参与定标；每一行按 `值 ÷ scale` 映射到 [0,1]。

```
dynAmount(p, base, catValue) = p * (base - catValue) / (1 - p)   // p 为小数
```

- `dynAmount` 在 `p >= 1` 时返回 null；UI 显示 `--`，不得崩溃。
- 差值：
  - `diffTarget = catValue - targetAmount`
  - `diffMin = catValue - minAmount`
  - `diffMax = catValue - maxAmount`
  - 首页范围条同时展示三个阈值（最低/标准/最高）的百分比、动态金额与差值：`+¥…` 表示当前高于该阈值，`-¥…` 表示低于，`持平` 表示相等；最低阈值差值为正时绿色、最高阈值差值为负时绿色，越界一侧为珊瑚色，目标差值为中性色。
  - 展开卡片时额外显示阈值名称（最低/标准/最高）。
- 类别范围条窗口（与 `portfolio.html` 一致）：
  - `span = maxPct - min(minPct, share)`
  - `pad = max(span × 0.15, 0.005)`
  - `barMin = max(0, min(minPct, share) - pad)`，`barMax = max(maxPct, share) + pad`
  - 所有刻度与标记在 `[barMin, barMax]` 内线性映射；窗口两端显示为整数百分比。
  - 轨道上：区间外淡珊瑚色、区间内淡绿色；min/max 灰色刻度、目标深色刻度、当前位置为白圈 + 状态色圆点。

### 4.3 黄金阈值价

```
goldGrams = Σ quantity(all positions in 黄金 category, kind=quoted gold)
otherValue = portfolioValue - goldCategoryValue
thresholdCnyPerGram(p) = p * otherValue / ((1 - p) * goldGrams)
```

- `p` 为下限/上限比例（小数）。
- 若当前金价源为 XAU：`thresholdUsdPerOz = thresholdCnyPerGram * 31.1035 / USDCNY`。
- 若为 AU9999：直接显示 `¥/克`。
- 黄金类市值包含各机构加价；阈值价沿用 invest-folio 的单一基准价近似（不区分机构），展示时注明为基准价。
- 首页黄金卡片仅在展开时于下限/上限刻度下显示阈值单克价（标准价不显示，折叠时不显示单价）；XAU 来源两行：先换算的 USD/盎司、再 CNY/克（换算所需 USD/CNY 缺失时不显示美元行），AU9999 来源显示 CNY/克。

### 4.4 盈亏（v1）

#### 定义

对区间 `[t0, t1]`：

```
P&L_category(c) = V_c(t1) - V_c(t0) - netFlowInto(c) - loss(c) - attribution(c)
P&L_total = Σ P&L_category(c)   （只对可计算的类别求和）
```

其中：

- `V(t)` 使用历史收盘价/净值估值，外币统一按**当前汇率**折算；缺失时按 4.5 用已知价格点插值，无法估值的端点标记「数据不完整」。
- `netFlowInto(c)` 包括（只统计 `from < date ≤ to` 的事件）：
  - 跨类别转账：仅当转出、转入类别都存在且不同；来源类别 `-fromNative × fx(来源币种)`，目标类别 `+toNative × fx(转入币种)`；单价为 null 时按 1；
  - 分红/利息（有 source 且来源/入账类别不同）：来源类别流出金额，现金类别流入同一金额；source=null（利息）或同类别不记流；
  - 外部入金/出金：不分 `capital`/`consumption`，一律记入对应现金类别（in 为正、out 为负）；
  - 调整事件：`deltaAmount` 优先，否则 `deltaQuantity × price`，乘当前汇率后记入对应类别；
  - 期初事件：发生在区间内的期初按「数量 × 价格（缺失时取当日历史价）」折算为价值；现金期初按金额；乘当前汇率后计入对应类别。区间开始之前的期初只影响 `V(t0)`，不记资金流。
- **不计入资金流**：
  - 支出事件：现金支出已通过回放扣减现金，体现在市值变化中；非现金损耗（position=null）通过 `loss(c)` 扣除；
  - 同类别内部的转账/分红：类别市值变化自然反映，按 0 净流处理；
  - 再投资：由用户手工录入买入转账，按转账处理。
- `loss(c)`：转账内嵌损耗 `lossAmount` 按 `lossCurrency` 用当前汇率折 CNY 后汇总；正值扣减盈亏，负值增加盈亏。归属规则：转入非现金 → 转入类别；否则转出非现金 → 转出类别；否则现金类别。**仅跨类别转账计入** `loss(c)`：同类别转账的损耗已通过该类别市值的自然下降体现，若再计入 `loss(c)` 会重复扣减（早期实现曾重复扣减，已修正）。旧数据中带 `linkedTransferId` 且备注为「转账手续费/转账损耗」的关联 expense 会被跳过，避免与内嵌损耗重复计算；独立现金手续费 `feeAmount` 已在回放中扣减现金，不再计入损耗。
- 标的级盈亏（类别盈亏的下钻）：`标的盈亏 = 期末市值 − 期初市值 − 标的资金流 − 归属该标的的损耗`。标的资金流是类别资金流的逐标的镜像，**额外覆盖同类别内部**的转账/分红两腿；损耗按 `transferLossCategory` 归属到单一标的（同类别转账也记录，跨类别归属到转入/转出标的）；source=null 的收入（利息）不记标的资金流，金额直接计入入账标的盈亏（与类别级口径一致）。同一类别各标的盈亏之和 = 该类别盈亏（跨类别支出保持类别级归属口径，可能略有出入）。期初/期末市值按区间两端、跨机构求和。
- `attribution(c)`：现金支出事件若 `categoryId` 不是现金类别，则在类别盈亏中把金额从现金类别转移到目标类别（总盈亏不变）：现金类别 P&L 加回该金额，目标类别 P&L 减去同一金额。
- 分红/利息的收益归属：有来源且跨类别时，来源类别盈亏 = 该资产的实际价格变化 + 分红金额，现金类别只做等额中转、不重复计收益；source=null 的利息直接计入现金类别。**App 不引入除息/复权概念**：基金价格按其实际单价涨跌计算，不区分下跌原因；分红与价格变化自然相加，是否立即转出不影响任何盈亏。v1 不单列「分红收入」，后续迭代再加。

#### 汇率

- 只使用**当前汇率**；不保存、不查询历史汇率。
- 估值、资金流、类别汇总都按当前汇率折 CNY；外币资产本身按原币显示。
- CNH v1 按 1:1 CNY。

#### 历史价格与缺口

- 收盘价/净值按日序列提供；周末/节假日沿用最近交易日收盘价（最多回溯 30 天）。
- 仍缺数据时用已知价格点线性插值，并记录日志「部分日期缺少线上历史价格，已用已知价格点线性插值」。
- 完全无法估值的日期：曲线断开；区间端点缺失时类别盈亏标记「数据不完整」，不输出伪造数字。
- 当前版本不提供手工补录价格的入口。

### 4.5 事件数据与价格回退

每笔事件只保存原币数量/金额/价格/费用/损耗，不保存汇率或 CNY 价值。外币估值和汇总使用当前汇率。

1. **审计**：查看每笔交易发生时的原币数量、价格、费用/损耗。
2. **价格回退顺序**（`HistoricalPriceProvider`）：同一天的 manual → auto；没有则向过去最多 30 天取最近收盘价；仍没有则用已知价格点（事件价格或缓存点）线性插值。
3. **缺口**：插值会记录日志；完全没有可用价格的日期返回 null，曲线与盈亏标记「数据不完整」。

不存在独立的「近似」结果标记；回退结果通过「数据不完整」与日志说明，不伪装成精确值。

### 4.6 总资产曲线

- 对每个自然日 `d ∈ [start, end]`：
  - 回放事件到 `d` 结束；
  - 对每个持仓取 `d` 的价格（缓存 → 向前 30 天最近收盘价 → 已知价格点插值）；
  - 计算 `V_total(d)`。
- 区间终点为今天时，用当前行情价格作为最后一个点（`currentPrices` 只在 `to == today` 时参与）。
- 曲线点若某持仓完全缺价：该点 `total=null`、标记不完整（折线断开），不填 0。
- 默认范围：类别盈亏为「月初至今」，其余图表为「年初至今」；可选月初至今、近 1 月、近 3 月、年初至今、近 1 年、全部、自定义。
- 数据来源：`HistoricalPriceService` + `price-cache.json`；历史抓取失败按持久化的 `lastFailedAtMs/exhaustedUntilMs` 指数退避。
- 图表页下拉刷新会重新抓取当前行情，并按价格指纹重建当前区间历史。

### 4.7 投入取出

- 数据来自 `external` 事件（**不区分 `capital`/`consumption`**）：
  - 投入：`direction=in` 的金额（CNY，当前汇率）按时间累加；
  - 取出：`direction=out` 的金额按时间累加；
  - 净投入 = 投入 − 取出。
- 不依赖历史行情，始终可计算。
- 时间范围与总资产曲线一致；按月聚合展示。

### 4.8 调仓统计

- 数据来源：`transfer` 事件中「转出与转入为不同标的」的交易；同一标的换机构、现金换汇、现金类别内部转换单独计数（`accountMoveCount` / `fxSwapCount` / `cashCategoryCount`），不计入调仓频次与金额。
- 转出或转入类别为卫星的转账不计入调仓笔数、金额、方向统计、类别流向与热门前几对（卫星不参与再平衡）。
- 方向：现金→资产、资产→现金、资产→资产；按月按方向统计笔数与金额，另有平均间隔天数、区间最近 30 天笔数、类别净流向（卖出/买入）、热门前几对（类别间转移次数与金额）。
- 金额：两端按当前汇率折算；无法定价的一端留空（该笔仍计数，结果标 incomplete）。
- 费用/损耗：转账内嵌损耗与独立现金手续费合计（旧数据中由转账差额生成、`linkedTransferId` 指向该转账的 expense 与转账二选一，避免重复）。
- 不依赖历史行情，始终可计算。原「费用统计」图表已下架，相关金额并入调仓统计脚注。

### 4.9 机构/币种分布

- 机构分布：按 `institutionId` 汇总当前 `valueCNY`；显示 `chartAlias ?: name`；金额为 0 的机构不显示。
- 币种分布：
  - 默认：`currencyExposure=auto` 时按标的 `currency`；CNH 归入 CNY；
  - 黄金默认归入 CNY；用户可把某黄金标的设为 `PHYSICAL_GOLD`；
  - 现金标的按 `currency`；
  - 支持覆盖为 `CNY/USD/HKD/PHYSICAL_GOLD`；
  - 金额为 0 的币种不显示。
- 占比分母为 `totalValue`。

### 4.10 精度

- 使用 `BigDecimal`；金额、数量、价格、比例均以十进制字符串序列化。
- 计算中间结果保留至少 8 位小数；展示按标的/币种精度四舍五入。
- 禁止 `Double` 参与金额累计；图表绘制可用 `Float`，但必须先由 `BigDecimal` 计算完成。
- 百分比展示：默认 1 位小数；目标比例按设置原样展示。

---

### 4.11 分红统计

- 只取 `kind=dividend` 的收益事件；日期区间含起止日；利息（`kind=interest`）不计入。
- 金额按**当前汇率**折 CNY；缺汇率的事件跳过并在结果中标记不完整。
- 来源：事件的来源持仓 → 标的与类别；无来源记为「无来源」。
- 去向：`immediateExternalWithdrawal` 或 `linkedEventId` 非空 → 「立即转出」，否则「留存现金」；另按「入账机构 · 现金标的」汇总入账账户。
- 输出：合计、笔数、月均（按有分红的月份）、单笔最大、最近一笔、月度列表（倒序）、来源/去向分组（按金额降序）。

### 4.12 资产曲线归因

- 曲线每日变化拆分为「外部资金流」与「市场盈亏」：外部入金/出金、调整、期初计为资金流；其余（价格、分红、费用/损耗）计为市场盈亏。
- 区间摘要：期初估值 + 出入金 + 市场盈亏 = 期末估值（`marketPnlCny`）。
- 每日资金流按类别记录，供图表页的出入金标记（入金 ▲ / 出金 ▼，默认隐藏）与提示使用。
- 剔除资金流曲线（`adjustedCurve`）：按日计算「当日变化 − 当日资金流」的收益率并滚动复利到期初值，只反映价格、分红与费用；某日缺价格则保留缺口，不改变指数。
- 跨类别转账、支出从现金付到非现金类别等内部转移按类别记录，不进入外部资金流。

## 5. 行情系统

### 5.1 原则

- **当前行情只使用首选数据源**：沿用 `portfolio.html` 中每个标的类型对应的首选源；不使用备选源。首选源失败即视为行情获取失败（D-11/FR-QUOTE-01）。
- **历史行情**使用每类数据的一个首选源；失败时显示缺口并按退避策略重试，不自动切换备选源（FR-HIST-03/04）。
- 网络请求不携带任何持仓金额；只携带标的代码。
- 所有请求设置 UA/Referer（按各接口需要）；不使用需要密钥的接口。

### 5.2 标的类型与路由

创建标的时按以下规则推断 `quote.source`（可在设置中覆盖）：

| 条件 | 类型 | 当前行情首选源 |
|---|---|---|
| `kind=cash` 且 currency=CNY/CNH | 人民币现金 | 无需请求，price=1 |
| `kind=cash` 且 currency=USD/HKD | 外币现金 | 汇率适配器（USD/HKD → CNY） |
| 6 位数字，类别=中债 或代码以 1/5 开头 | 场内 ETF/债券 | Tencent kline 现价 |
| 其余 6 位数字 | 场外基金 | Eastmoney 基金批量接口 |
| 2-5 位大写字母 | 美股/美股 ETF | Tencent US |
| 5 位数字 | 港股 | Tencent HK（`hk` + 补零到 5 位的代码） |
| `AU9999` | 黄金 | 用户在 AU9999 与 XAU 中选择的默认源（只用一个） |
| 其他 | 未识别 | 手工价格（`quote.mode=manual`） |

- 显式 `quote.source` 优先于代码推断：`manual`、`fund/eastmoneyfund/fundbatch`、`etf/tencentetf/bond`、`us/tencentus`、`hk/tencenthk`、`au9999/sge`、`xau`；未识别的值会得到 `Unknown(source)` 并在门禁中失败。
- `quote.mode=none` 对 quoted 标的没有特殊处理，仍按上面的规则推断。
- 同一标的只取一次；多个机构持仓共享结果。
- 手工价格视为有效价格源，不触发启动门禁失败。

### 5.3 当前行情适配器（首选源，逐条移植 portfolio.html）

1. **汇率**：`https://open.er-api.com/v6/latest/USD` 获取 USD/CNY、HKD/CNY；不使用 Eastmoney fallback。CNH v1 视作 CNY。
2. **场外基金**：Eastmoney 基金批量接口（Fcodes 逗号分隔）；优先 `NEWPRICE`，否则 `NAV`；重试 1 次（固定）。不使用 fundgz/pingzhongdata 作为当前价格 fallback（pingzhongdata 仅用于历史净值）。
3. **场内 ETF/债券**：Tencent `web.ifzq.gtimg.cn/appstock/app/fqkline/get`，取 `qt[3]`；不使用 Eastmoney fallback。
4. **美股/ETF**：Tencent `us{SYMBOL}`，取 `qt[3]`；盘前/盘后 `pandata.last` 覆盖收盘；不使用 Eastmoney fallback。
5. **港股**：Tencent `hk{5 位数字代码}`（不足 5 位左侧补零），取 `qt[3]`；币种 HKD；历史日线走同一 Tencent kline（港股返回不复权的 `day` 数组）。
6. **黄金**：用户在设置中选择默认源：
   - AU9999：Eastmoney push2 `118.AU9999`（CNY/克）；
   - XAU：`https://api.gold-api.com/price/XAU`（USD/盎司 ÷ 31.1035 × USD/CNY）。
   - 只使用选中的源；失败即失败。每个机构加价在估值时叠加。
7. **手工价格**：直接使用 `quote.manualPrice`，不发起网络请求。

实现注意：

- `pingzhongdata` 返回 JS 文本，仅用于历史净值解析，需要在 Kotlin 中按文本正则/字符串解析。
- 端点可能随时变化；每个适配器必须返回结构化错误（source、url 概要、异常），供调试页展示。
- 精度：基金净值 4 位、ETF 3 位、美股 2 位、黄金 2 位、汇率 6 位；具体按接口返回和标设置。
- HTTP 客户端为 OkHttp：连接/读取超时 8s、call 超时 12s；UA 为移动端 Chrome UA，东财/腾讯请求带对应 Referer。
- 请求按类型分组：汇率 1 次、基金 1 次批量、ETF/美股每个标的并发（无并发上限）、黄金每个源 1 次。

### 5.4 启动价格门禁

1. 从当前持仓中收集所有 `quantity > 0` 的 `(instrument, institution)`。
2. 对每个 `instrument` 生成必需报价：
   - `kind=cash`：若 currency=CNY/CNH → 本地 price=1；否则需要对应 FX。
   - `kind=quoted`：`quote.mode=manual` → 本地手工价；否则需要 `quote.source` 成功（只用首选源）。
   - 黄金：需要金价；若 XAU 源还需要 USD/CNY。
3. 对每个非 CNY 币种（USD/HKD）生成必需 FX 请求。
4. 按类型分组执行（汇率单请求、基金批量、ETF/美股并发、黄金按源）；固定超时与 1 次重试；不切换备选源。
5. 任一必需项失败 → 构造失败列表、`QuoteSnapshot` 日志与失败项，`AppUiState.QuoteFailed`，显示调试页。
6. 全部成功 → 更新内存报价 → 进入首页（不写历史缓存）。

**不使用缓存**：门禁判断不读取 `price-cache.json`；也不使用上一次会话的内存价格。启动必须重新获取。

### 5.5 调试页

全屏 Composable（`QuoteFailureScreen`），不显示底部导航，没有返回/关闭按钮；只有刷新成功或设置手工价格后重试成功才会回到主界面。

- **标题**：「行情获取失败」，说明「任一必需价格获取失败时不会进入主界面。」
- **失败列表**：每个失败卡片显示标的代码/名称、机构名、`kind` 与来源、错误信息；不显示失败时间。
- **日志**：最近 200 行应用日志，等宽字体、可选中复制；无日志显示「（无日志）」。
- **按钮**：
  - 底部「刷新」：重新执行行情获取；
  - 底部「保存日志」：写入 `<数据目录>/logs/folio-prism-<yyyy-MM-dd'T'HH-mm-ss>.log`（内容为本次失败日志），Toast 提示路径；
  - 每个带 `instrumentId` 的失败卡片内「设置手工价格并重试」：输入 > 0 的价格，写回标的（`quote.mode=manual`）后重新获取行情。汇率等无标的的失败项没有此按钮。
- 存储权限缺失时不会进入本页（由权限说明页处理）；数据文件损坏进入「加载失败」页。
- 日志可包含标的代码/名称、来源与错误；**不记录金额、数量、汇率**。

### 5.6 历史行情适配器（首选源）

> 以下数据源已在 Redmi 14C 上验证；PC 端测试结果不代表手机端。

| 数据类型 | 首选源（已在 Redmi 14C 上验证） |
|---|---|
| 场外基金净值历史 | `fund.eastmoney.com/pingzhongdata/{code}.js` 的 `Data_netWorthTrend` |
| 场内 ETF/债券 | Tencent kline（`fqkline/get?param={sh|sz}{code},day,...`） |
| 美股/美股 ETF | 新浪财经美股日线：`stock.finance.sina.com.cn/usstock/api/jsonp.php/.../US_MinKService.getDailyK?symbol={SYMBOL}` |
| 黄金 | 新浪财经上海金期货连续合约 AU0：`InnerFuturesNewService.getDailyKLine?symbol=AU0`；**作为 AU9999 现货历史的近似**，因为 XAU 历史源需要 API key |

- **不抓取历史汇率**：曲线、资金流、损耗全部使用当前汇率。
- 东财 `push2his` 的 K 线接口在目标手机上会被断流，因此不作为历史源。
- 每类只配置一个首选源；失败时按 FR-HIST-04 处理，不自动切换备选源。
- 每个适配器返回 `Map<LocalDate, BigDecimal>`；失败时返回结构化错误。
- 抓取范围：对每个标的从区间起点前约 10 天抓到区间终点（不是逐缺口增量抓取）；缓存命中判定为「起点前 30 天内有数据，且末端已到区间结束前的最近工作日（周末回退），再放宽 3 天以覆盖短假期」。过期的缓存必须重拉——否则估值会用旧收盘价代替区间起点，而终点使用实时价，区间之外的涨跌会被算进该区间盈亏。长假期间数据源确无新数据时，由「抓取成功但未覆盖」的 `exhaustedUntilMs` 记录兜底，避免反复请求。
- 失败退避：内存负缓存 60s；失败时间与 `exhaustedUntilMs` 持久化到 `price-cache.json`。重复失败从 24 小时起翻倍，上限 30 天；首次失败只记录时间。
- 黄金历史在 UI 日志中标注 `sina AU0 futures (proxy for AU9999)`；后续若拿到可靠的 AU9999/XAU 历史源可替换。

### 5.7 历史数据获取与缺口

- 用户查询区间 `[t0, t1]`：
  1. 根据每个标的的缓存覆盖情况决定是否需要联网抓取（覆盖判定见 5.6）；
  2. 需要时按首选适配器抓取 `[t0 − 10 天, t1]`；
  3. 合并写入缓存并保存；
  4. 计算曲线/盈亏。
- 具体估值时的价格回退顺序见 4.5：当天 manual > auto → 过去 30 天最近收盘价 → 已知价格点线性插值。
- 插值会写日志；完全没有价格的日期曲线断开，区间端点缺失时盈亏标记「数据不完整」。
- 当前版本没有手工补录入口，`manual` 缓存只读。
- 缓存文件损坏/丢失：读取时视为空缓存并重新抓取；不影响主数据。
- 缓存保存失败：历史页进入错误态（显示错误与重试），不静默忽略。
- 历史抓取失败：显示错误与重试；失败退避持久化，避免频繁请求数据源。

---

## 6. 数据存储与备份

### 6.1 固定目录与权限

- 固定路径：`/storage/emulated/0/Documents/folio-prism/`（通过 `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS)` 解析，避免硬编码）。
- 不提供目录选择，不制作应用内启动设置页（D-21）。
- Android 11+ 需要 `MANAGE_EXTERNAL_STORAGE`（「所有文件访问」）：
  - 在 Manifest 声明；
  - 启动时检查 `Environment.isExternalStorageManager()`；
  - 未授予：显示权限说明页，通过 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 跳转系统授权页；未授权期间不写入数据。
- 使用直接文件 I/O（`java.io.File`），不使用 SAF/DocumentFile。
- 该权限适合自用 sideloaded APK；不涉及应用商店政策。

### 6.2 目录与文件

```
Documents/folio-prism/
  folio-prism.json                    主数据
  folio-prism.json.tmp                写入临时文件（仅保存过程中存在）
  price-cache.json                    历史价格缓存
  price-cache.json.tmp                缓存写入临时文件
  backups/
    folio-prism-2026-11-19T10-30-00-000.json
  logs/
    folio-prism-2026-11-19T10-30-00.log
```

- README 写明路径；用户用文件管理器复制上述文件即为备份。
- App 不提供导入/导出 UI；允许在 App 关闭时手工编辑 JSON（见 6.6）。
- 只自动备份主数据，不备份 `price-cache.json`；如需恢复，用户把 `backups/` 中的文件复制回目录并改名为 `folio-prism.json`（App 不提供恢复入口）。

### 6.3 主数据原子写入协议

1. 仓储先把 `meta.updatedAt` 更新为当前时间、事件按时间排序，再将 JSON 序列化为 pretty print 文本。
2. 写入临时文件 `folio-prism.json.tmp`（UTF-8，无 fsync）。
3. 重新读取临时文件并解码校验；失败则中止，保留原文件。
4. 若主文件存在，复制到 `backups/folio-prism-<yyyy-MM-dd'T'HH-mm-ss-SSS>.json`；备份失败则中止保存。
5. 删除旧 `folio-prism.json`，将临时文件重命名为主文件；重命名失败时用「复制 + 删除临时文件」兜底。
6. 按 `retainCount`（默认 5）按修改时间保留最新 N 份备份、删除其余。
7. 事件自身的 `updatedAt` 不更新。

**启动恢复**：

- `folio-prism.json` 缺失 → 直接创建默认空数据（预置 7 类规则），不读取 `.tmp`、不检查备份。
- 主文件损坏/无法解析 → 全屏「加载失败」页，显示错误信息，可重试；不会自动覆盖或列出备份。
- 用户如需恢复，关闭 App 后用文件管理器从 `backups/` 复制文件。

### 6.4 价格缓存写入

- 采用「写 `price-cache.json.tmp` → 解码校验 → 删除旧文件 → 重命名」；失败则用「复制 + 删除临时文件」兜底。
- 缓存保存失败会向上抛出，历史页进入错误态；不会静默忽略。
- 缓存不做自动备份；缓存文件不参与当前估值门禁。
- 读取失败时静默视为空缓存（`PriceCacheData()`），下次抓取重新填充。

### 6.5 Schema 版本

- `schemaVersion` 为整数，当前为 1；每次不兼容变更应 +1。
- 当前版本**没有迁移函数链/版本分支**：读取时只解码为当前模型；不兼容的旧文件需要用户借备份手工处理。
- 文件使用了未知字段时会解码失败（`ignoreUnknownKeys=false`），显示加载失败页。

### 6.6 手工编辑 JSON

- 用户可以在 App 关闭时用文本编辑器修改 `folio-prism.json`（D-17）。
- 启动时严格校验 JSON 结构：语法、必填字段、字段类型、未知字段；错误时显示全屏「加载失败」页与错误信息，不静默修复；不解析具体错误位置。
- App 运行期间的外部修改可能被下一次保存覆盖；README 提示「先关闭 App 再编辑」。
- 手工编辑不保证业务正确性（负持仓、负现金等允许，由用户负责）。

### 6.7 精度与序列化

- `BigDecimal` 通过自定义 serializer 写为 JSON 字符串（如 `"1.0791"`）。
- 读写时保持原字符串，不做 `Double` 转换。
- ID 使用 UUID 字符串；事件 ID 一旦生成不复用。
- 1 万条事件的目标规模：JSON 预计几 MB 到十几 MB；启动解析与回放需在可接受时间内（见测试）。

---

## 7. UI 设计

### 7.1 导航

底部导航 4 项：

1. **首页**（总资产 + 7 类卡片）
2. **图表**（7 个图表）
3. **交易**（录入 + 流水）
4. **设置**

调试页是顶层状态分支（不是导航路由），全屏显示、不显示底部导航。首次启动不设应用内设置页；仅在缺少存储权限时跳转系统授权页（由权限说明页承载）。

### 7.2 首页

- 顶部不设应用标题与刷新按钮；下拉刷新。
- 首页滚动后，系统返回手势/返回键先回到首页顶部；已在顶部时按返回退出 App。
- 总资产卡片：总资产、红利小计与占比、组合小计与占比；存在卫星类别时追加「卫星小计与占比」（三者合计为总资产的 100%）。
- 资产配置总览卡片（位于总资产卡片下方）：
  - 7 行，一行一个战略类别，一屏即可看到全部 7 类；配置卫星类别时其后追加卫星行（色点 + 名称，无目标/区间文字；条形只画填充，无区间带与目标线；右侧金额与占总资产比例（中性色）+ 箭头），点击滚动到卫星卡片；
  - 每行：类别色点 + 名称 + 目标占比；横向进度条（类别色填充、绿色区间带、min/max 灰色刻度、目标深色刻度，全图共用 `overviewScale`）；右侧当前金额与当前占比（区间内绿色、偏离区间珊瑚红）与箭头；
  - 点击任意行：展开对应类别卡片并平滑滚动到该卡片；
  - 数据不完整的类别在目标占比位置显示「数据不完整」。
- 类别卡片（LazyColumn）：
  - 标题行：色点 + 类别名称、当前占比、展开/收起图标；
  - 大号当前金额；`目标 ¥… · x%` 与 `区间 a% – b%`；
  - 范围条：自适应窗口（4.2），区间外淡珊瑚、区间内淡绿；min/max 灰色刻度、目标深色刻度、当前位置白圈 + 状态色圆点；
  - 圆点上方显示当前百分比（状态色）与紧凑金额（万/亿）；
  - 坐标行：窗口两端百分比；最低/标准/最高三列各自的百分比与动态金额；展开时额外显示阈值名称与差值；
  - 黄金类别展开时在最低/最高列额外显示阈值单克价：XAU 两行（`$x/盎司` + 换算的 `¥x/克`），AU9999 为 `¥x/克`；折叠时不显示；标准（目标）列不显示；
  - 若类别内有美元计价资产，显示「美元资产 $x」原币小计（无 CNY 等值）；
  - 缺失价格时显示「部分价格缺失，估值不完整」；首页顶部有「部分标的价格缺失」横幅；
  - 点击展开持仓明细：代码/名称、机构、数量 × 单价（原币）、原币市值（无 CNY 等值）；每次展开/收起有尺寸动画。
- 卫星卡片（仅当配置了卫星类别时，位于七张类别卡片之后）：
  - 紧凑卡片，视觉弱于七张战略类别卡片：色点 + 名称 + 「不计入再平衡」+ 当前金额 + 「占总资产 x%」+ 展开图标；
  - 不画范围条、不显示目标/区间、不使用区间内外状态色；可展开持仓明细（同样的 HoldingRow）；
  - 缺失价格时显示「部分价格缺失，估值不完整」。
- 空状态：无持仓时资产配置总览与 7 张战略类别卡片照常显示（金额 ¥0、占比 0%、范围条绘制在 0% 位置；资产配置中的卫星行与已配置的卫星卡片同样显示 0），不显示「暂无数据」占位，不主动引导（FR-ONBOARD-03）。总资产为 0 时占比按 0 处理（除数为 0 时取 0，不做除法）。

### 7.3 图表页

- 顶部为图表类型下拉菜单：`总资产` / `类别盈亏` / `投入取出` / `调仓统计` / `分红统计` / `机构分布` / `币种分布`；默认打开「类别盈亏」。原「类别资产」页已并入「总资产」页：页头下方的类别下拉菜单提供「总资产 + 各个类别（含卫星）」。
- 默认区间：类别盈亏为「月初至今」，其余带区间的图表为「年初至今」。切换图表时，如果当前区间仍等于旧图表的默认区间，则切换为新图表的默认区间；用户自定义过的区间保持不变。
- 底部区间栏（总资产、类别盈亏、投入取出、调仓统计、分红统计五类可见）：`‹  2026.1.1 ~ 2026.11.12  ›`。左右箭头按**整月**平移并限制到今天；点击中间文本打开区间选择对话框。机构/币种分布没有区间栏。
- 区间选择对话框：
  - 快捷预设：月初至今、近 1 月、近 3 月、年初至今、近 1 年、全部；
  - 当日即为月初或年初时，月初至今/年初至今为单日区间：图表以居中的水平线 + 单点展示，区间盈亏为 0，不报错；
  - 开始/结束日期分别打开 Material 日期选择器（范围：今天前 10 年至今天），既可日历选择，也可切换输入模式键入日期；
  - 确认后按新区间重新计算；历史行情按需抓取并复用缓存。
- **总资产 / 类别资产（合并页）**：页头下方提供「类别」下拉菜单，选项为「总资产 + 各个类别（含卫星）」；选择总资产时显示全组合曲线与区间盈亏，选择某类别时只统计该类别（曲线、区间盈亏、当前类别金额与持仓明细）。
  - 折线图（Compose Canvas）+ 区间盈亏大数字 + 期初/期末/曲线变化 + 归因摘要（期初 + 出入金 + 市场盈亏 = 期末）；「含资金流 / 剔除资金流」曲线切换（4.12）；可叠加出入金标记（默认隐藏）；缺失数据断点；区间终点为今天时使用当前行情；
  - 切换类别即时更新，复用同一份历史结果，不重新请求行情；下方「当前类别金额」表在总资产范围显示全部类别，在类别范围只显示该类别。
- **类别盈亏**：各配置类别（含卫星）的列表（图标、占比、彩色进度条、盈亏金额）；表头显示合计；历史数据不足的类别标记「数据不完整（缺少历史价格）」。
  - 点击某一类别可展开一个下拉卡片，列出该类别各标的在**相同时间区间**内的盈亏金额与区间收益率（盈亏 ÷ 期初市值，期初为 0 时不显示百分比）；按盈亏降序（空值垫底），卡片底色浅灰、圆角，与列表行区分。展开为纯视图状态，按区间记忆，切换区间自动收起，不重新请求行情。该类别无标的时显示「该类别暂无标的明细」。
- **投入取出**：区间净投入大数字 + 按月的投入/取出/净投入列表；不依赖历史行情。
- **调仓统计**（4.8）：区间调仓笔数与金额、按方向分月的柱状、净流向行、热门前几对，以及费用/损耗合计与口径脚注；不依赖历史行情。
  - 点击类别净流向行或热门调仓对行可展开一个下拉卡片，列出该切片对应的转账流水，每条三行：转出标的、转入标的（均为代码 + 名称，各占整行）、日期与金额（金额居右，有损耗时日期后附「损耗」）；按日期降序，卡片底色浅灰、圆角，与列表行区分。同一块内同时只展开一行；展开为纯视图状态，按区间记忆，切换区间自动收起，不重新请求行情。
- **分红统计**：区间分红合计大数字 + 概览（笔数、月均、单笔最大、最近一笔）+ 去向分布（立即转出/留存现金）+ 来源分布（类别/标的）+ 入账账户 + 月度分红；只统计分红事件（不含利息），按当前汇率折算。
  - 点击任一分布块的行（去向分布、来源类别、来源标的、入账账户、月度分红）可展开一个下拉卡片，列出该切片对应的分红流水，每条三行：来源标的（代码 + 名称，整行）、入账账户（机构 · 标的）、日期与金额（金额居右，立即转出的日期后附「立即转出」标记）；按日期降序，卡片底色浅灰、圆角，与列表行区分。同一块内同时只展开一行；展开为纯视图状态，按区间记忆，切换区间自动收起，不重新请求行情。
- **机构分布**：横向条形列表（金额、占比），按金额降序；显示机构别名；金额为 0 的机构不显示。
- **币种分布**：同上，类别为 CNY/USD/HKD/实物黄金；CNH 归入 CNY；金额为 0 的币种不显示。
- 图表页支持下拉刷新：重新获取当前行情，并按价格指纹重建当前区间历史；加载中显示进度与「正在获取历史行情…」（需要联网时），失败显示错误与「重试」；无数据时显示各自的空状态文案。

### 7.4 交易页

- 顶部：标题 + 金色圆形「+」按钮 + 金色「筛选」按钮（已选条件数显示在按钮上）。
- 筛选面板：底部弹出，分段为类型（全部/转账/收益/外部/支出/期初/调整）、机构、标的；每段单选、可组合（机构与标的须命中同一持仓），选择即时生效，底部按钮显示命中笔数并关闭面板；已选条件以可移除标签显示在标题下方，可一键清除。
- 列表：按时间倒序、按日分组（日期 + 星期 + 当日笔数），组内为白色圆角列表；每行显示类型图标、类型、机构/标的简称、金额/数量、备注（若有）；列表不常驻编辑/删除图标。
- 点击整行打开「交易详情」对话框（完整字段 + 「编辑」入口），从详情进入编辑页。
- 删除：编辑页顶栏的删除按钮，二次确认后删除；同时清理关联事件（转账的旧关联 expense、收益的关联外部出金）。编辑已有事件时类型页签禁用，不能改类型。
- 编辑页（参考随手记设计语言）：
  - 顶栏：返回、居中标题、金色「✓ 保存」；
  - 类型页签：转账 / 收益 / 支出 / 外部 / 期初 五个文字页签 + 溢出 chevron（选中后显示「调整」）；金色下划线标识当前类型；
  - 金额区：大字号金额输入 + 原币/单位 + 自动折算的 CNY 等值；转账页显示转出/转入两段金额与损耗摘要；
  - 账户区：转出/转入两列显示机构与标的，中间按钮可交换两侧；点击打开底部持仓选择面板（机构 Chip + 标的列表）；
  - 价格区：按当前行情或手工价预填单价，可修改；外币标的的单价可切换「原币 / CNY」两种输入币种（CNY 按当前汇率换算为原币存储，并显示折合原币价格）。预填只写入空字段或仍保留上次建议值的字段；用户手填后不再覆盖；清空字段后重新允许填入；切换标的后直接改用新标的报价。手续费/损耗按转出币种输入，可指定是否从独立现金账户扣；
  - 转账自动建议：转入数量按价值自动换算（建议不覆盖用户输入；可点击「按价值换算：X 单位」手动应用）；来源持仓旁「自动」按钮按类别目标占比建议转出数量；转入侧另有「按目标占比：X 单位」建议，点击后同时填好转出与转入数量（所需价值超过转出持仓时按上限填写并标注）；
  - 时间行：日期打开日期选择器，时间胶囊打开时间选择器；备注行；保存只用顶栏右侧的金色「✓ 保存」，校验错误显示在类型页签下方。
  - **转账**：保存为单个 `TransferEvent`；差额写入 `lossAmount`（同币种自动计算；跨币种在两端均可精确折算为人民币时自动计算，否则手动输入），独立现金手续费写入 `feePosition/feeAmount/feeCurrency`；不生成关联 expense 事件。
  - **收益**：分红/利息；来源可选；入账现金账户；金额；是否立即转出（生成关联外部出金）。
  - **外部入金/出金**：本金/消费。
  - **支出**：手续费/损耗；金额可为负值；选择现金账户或非现金损耗。
  - **期初**：期初持仓/现金；每种机构 × 标的组合只允许一笔期初事件，保存第二笔时拒绝并提示已有期初的日期。
  - **调整**：数量/金额调整 + 原因。
- 表单校验只做格式/必填校验；不阻止负持仓/负现金；超额转出只警告不阻止。
- 空状态：显示空状态即可。

### 7.5 设置页

主页面分为三块：

- **数据**：显示数据目录绝对路径、机构/标的/事件数量、`备份保留 N 份`（默认 5，仅展示，不在 UI 中修改）。不显示数据文件路径、缓存路径、备份列表或最近备份时间。
- **局域网数据服务**：开关；运行时显示可复制的 `http://<IP>:<端口>`；失败时显示错误；开启时提示「服务运行期间请勿在手机端编辑数据；网页端的修改需重启 app 后才能在手机上显示。」
- **管理**：
  - 机构：显示数量，进入机构管理页；
  - 标的：显示数量，进入标的页；
  - 类别与再平衡：显示 7 类数量，进入类别页；
  - 黄金价格修正：右侧显示当前金价源（XAU/AU9999），进入黄金页。

机构管理（参考随手记账户管理页）：

- 独立页面，顶栏：返回 / 机构 / 金色「+」；底部导航保留。
- 按机构类型分组（银行 / 券商 / 其他）：灰色分组标题 + 数量；全宽白色列表；每行细线图标、名称、类型/图表别名/备注、chevron。
- 点击行进入编辑表单，`+` 新增；表单为大字号名称输入 + 类型/别名/备注白色分组，顶部删除、底部金色保存。
- 删除需二次确认；有事件引用时拒绝删除并显示原因。

标的：

- 独立页面，顶栏：返回 / 标的 / 金色「+」。
- 按类别分组（类别色点 + 名称 + 数量）；全宽白色列表；每行类别图标、代码/名称、币种/单位/类型/行情方式、chevron。
- 表单为大字号名称输入 + 代码/标的类型/类别/币种/单位、行情方式/手工价格、价格精度/数量精度/币种暴露、备注；顶部删除、底部金色保存；有事件引用时拒绝删除。
- 新增标的时，代码行末尾有低调的「获取名称」小按钮（仅新增且非现金、非手工价格时显示；代码为空时置灰）。点击后按实时行情同一套路由查询名称（基金走东财批量接口、ETF/债券与美股/港股走腾讯接口、黄金代码固定为 AU9999/XAU 不请求），查询中按钮内显示小转圈。名称字段非空时点击不查询，代码行下方以珊瑚色小字提示「名称不为空，请先清空名称再获取」，清空名称后提示消失；查不到或请求失败则静默不填。

类别与再平衡页：

- 独立页面，顶栏：返回 / 类别与再平衡（无新增按钮，7 个战略类别固定；卫星类别在新增/编辑标的时选择「卫星」自动创建）。
- 分组「再平衡目标」+ 战略类别数量；全宽白色列表，每行类别图标/颜色、名称、目标/区间百分比、chevron。
- 若配置了卫星类别，追加分组「卫星资产」：副标题「计入总资产 · 不参与再平衡 · 名称内置」；整行为只读（无点击、无 chevron）。
- 编辑表单：顶部只读显示内置类别名称（附「名称内置，不可修改」）+ 目标/最低/最高百分比三行（右对齐 + `%` 后缀）；底部金色保存；不允许删除类别，也不提供名称/颜色/顺序编辑。

黄金价格修正页：

- 独立页面，顶栏：返回 / 黄金价格修正。
- 「金价源」单行：当前来源（国际金价 XAU / 上海金 AU9999），点击进入下拉表单。
- 分组「机构加价」+ 机构数量；每行机构图标/名称、`加价 ¥x/克`、chevron。
- 加价表单：大字号金额输入，单位 CNY/克，允许负数；底部金色保存。

删除保护：

- 有事件引用的机构/标的不可删除（结构性引用保护）。
- 修改标的类别会改变历史统计归属；当前版本不弹确认提示（结构性变更由用户自行负责，可借助备份回滚）。
- 删除类别不允许（7 个战略类别固定；卫星类别需在 JSON 中移除）。

### 7.6 调试页

见 5.5。全屏错误状态（标题「行情获取失败」），不显示 App 版本与数据路径；失败列表可滚动，日志区等宽字体；底部固定「刷新」「保存日志」，手工价格按钮位于各失败卡片内。

### 7.7 首次启动与空状态

1. 检查存储权限；未授予 → 权限说明页 + 系统授权页。
2. 创建 `Documents/folio-prism/` 与 `folio-prism.json`（**不创建** `price-cache.json`）；预置 7 类默认规则与颜色（顺序 1–7）：红利目标 32%（区间 28.8%–35.2%），其余 6 类目标各为去除红利后基数的 1/6（≈16.67%，区间 15%–18.33%）。主 JSON 的 `settings.categories` 是再平衡规则的唯一权威来源，默认值只在文件缺失时用于新建。
3. 直接进入首页：无持仓时展示 7 类 0% 份额与目标区间（7.2），不设应用内启动设置页，不主动引导。

### 7.8 主题与格式化

- Material 3 light；中文 locale。
- 金额格式化：千分位、¥/$/HK$ 前缀、按精度四舍五入；范围条标注使用万/亿紧凑格式（如 `¥316万`、`¥1.23亿`）。
- 单项资产按原币显示；不显示 CNY 等值。
- 黄金阈值单克价（仅展开时显示）：XAU 两行（`$x/盎司` 2 位小数 + 换算的 `¥x/克`），AU9999 显示 `¥x/克`。
- 百分比：1 位小数（目标比例按输入展示）。
- 颜色：沿用 invest-folio 的类别颜色（JSON 中可改）：红利 `#ff7675`、成长 `#e17055`、美股 `#f9a825`、美债 `#0984e3`、中债 `#6c5ce7`、黄金 `#f9ca24`、现金 `#b2bec3`。
- 表格/条形图使用 Compose 组件，避免 WebView。

### 7.9 局域网 Web 工作台

- 设置页开关启动前台服务（dataSync 类型）；通知常驻，可从通知停止；服务停止后页面不可访问。
- 端口从 8420 起尝试到 8430；页面显示 `http://<手机局域网 IP>:<端口>`，取不到局域网地址时回退 `127.0.0.1`。
- 页面与功能：
  - 概览：总资产、红利/组合/卫星（有卫星时）、7 类卡片（目标区间条、阈值标注、持仓明细，与 App 同口径，来自 `/api/overview`）；卫星类别只显示紧凑卡片（金额、占总资产比例、持仓），不进资产配置条；
  - 图表：8 种图表（含调仓统计与分红统计，口径同 App，资产曲线含归因与剔除资金流曲线）+ 区间预设（`/api/chart`、`/api/flows`、`/api/rebalance`、`/api/dividends`）；
  - 流水：按类型/机构/标的/关键词筛选（机构与标的须由同一仓位满足，与 App 同规则），新增/编辑/删除 6 类事件（`POST/PUT/DELETE /api/events`），刷新行情（`POST /api/quotes/refresh`）；可追踪一个现金持仓，显示每笔影响它的交易前后余额（按事件时间顺序精确计算）；
  - 数据文件：顶栏「下载数据」以附件形式原样下载 `folio-prism.json`（`GET /api/download`）；「上传数据」仅接受名为 `folio-prism.json` 的文件，服务端用数据文件严格反序列化器校验格式（未知字段即拒绝），通过后才经 `PortfolioRepository` 原子替换手机上的文件（`POST /api/upload`）。
  - APK 升级（D-34）：顶栏「上传APK」以 `PUT /api/apk?name=<文件名>` 发送原始二进制（`Content-Type: application/octet-stream`）；服务端把请求体落为临时文件（二进制安全），`WebPortfolioApi.uploadApk` 校验文件名（拒绝空名、`.`/`..`、含 `/` `\` NUL；须以 `.apk` 结尾，忽略大小写）与内容（非空、前 4 字节为 ZIP 本地文件头 `PK\x03\x04`）后，覆盖写入数据文件同目录（`Documents/folio-prism/`），复用「所有文件访问」权限；响应发出后删除临时文件。
- 静态资源打包在 `app/src/main/assets/web/`（含 Chart.js），不访问外网；没有鉴权、HTTPS、CORS 校验，只适合可信局域网。
- 事件写入复用 `PortfolioRepository`（备份 + 原子写 + 更新 `meta.updatedAt`）；网页端的行情快照只在内存，历史复用 `price-cache.json`。
- 未读取请求体即拒绝的请求（405/404）：服务端按 `Content-Length` 丢弃剩余请求体后再响应（drain）。否则残留字节会留在 keep-alive 连接上，与下一个请求拼接成非法请求行（表现为 `xGET` 之类的 400）。
- 已知限制：仓储没有写锁，App 与 Web 同时写事件可能丢失更新；网页修改需重启 App 才能在手机上看到。

---

## 8. 构建与安装

### 8.1 工具链（开发机）

当前安装在用户目录（无需 root）：

- JDK 17：`~/.local/opt/jdk-17`；
- Android SDK：`~/.local/share/android-sdk`（platform-tools、SDK Platform 35、Build Tools）；
- AGP 8.7.3、Kotlin 2.0.21、Gradle Wrapper；compileSdk/targetSdk 35、minSdk 26、Java/Kotlin 目标 17；
- `local.properties` 写 `sdk.dir=...`（不提交 git）；构建时设置 `JAVA_HOME`/`ANDROID_SDK_ROOT`。

### 8.2 构建与安装

```
export JAVA_HOME=$HOME/.local/opt/jdk-17
export ANDROID_SDK_ROOT=$HOME/.local/share/android-sdk

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.jimbozhang.folioprism/.MainActivity
```

- debug 签名即可；不做 release 签名/上架。
- Redmi 14C 需要开启开发者选项与 USB 安装；adb 已可连接。
- 目标机 Xiaomi 17 Ultra 通过同样方式安装。
- 每次代码改动后应重新构建 APK 并安装到调试机验证。

### 8.3 版本管理

- 遵循现有 `.gitignore`（Gradle/Android/IDE/keystore 等）。
- 不提交 `local.properties`、`build/`、`.gradle/`。
- 文档与代码同仓库；README 记录构建/安装/数据路径。

---

## 9. 测试策略

### 9.1 单元测试（JVM）

当前 `app/src/test` 下共 27 个测试类、228 个用例：

- `domain/calc`：事件回放、估值（现金/有净值/汇率/黄金加价）、快照（双口径/dynAmount/视觉占比/总览缩放/黄金阈值价/零资产全 0%）、历史盈亏、历史价格回退、转账建议与单价建议优先级、范围条窗口、分红统计、调仓统计、JSON 序列化。
- `domain/history`：区间预设与起止日期计算。
- `domain/quote`：行情路由（代码/类别/显式 source）。
- `data/price`：QuoteService 解析与失败处理。
- `data/history`：历史行情抓取、缓存合并与缓存新鲜度判定。
- `data/json`：默认数据、序列化不含流水记录时间、旧文件兼容。
- `data/store`：仓储加载/保存/备份。
- `data/web`：HTTP 服务、事件构建、概览 API。
- `ui/components`：金额格式化、持仓选择过滤。
- `ui/transactions`：期初唯一性、转账手续费、分红来源过滤、交易筛选（类型/机构/标的组合）。

待补充：Compose UI 测试（当前没有 `androidTest`）、1 万条事件的性能基准、真实接口的录制 fixture。

### 9.2 UI/集成测试

- 当前没有 Compose UI 测试与 instrumentation 测试；UI 主要在调试机上手工验收。
- 如需补充：权限页、空状态、首页卡片展开、转账/支出录入、调试页保存日志/手工价格、Web 工作台页面。

### 9.3 手工验收

- 在 Redmi 14C 与 Xiaomi 17 Ultra 上：
  - 首次启动、存储授权、空状态；
  - 断网启动 → 调试页；恢复网络 → 刷新进入；
  - 单项行情失败 → 调试页；设置手工价格 → 进入；
  - 数据文件在文件管理器中可见、可复制；
  - 手工编辑 JSON（App 关闭时）→ 启动加载/校验；
  - 首页总览/范围条/展开动画、黄金单克价（XAU 与 AU9999 两种来源）；
  - 图表 7 种、区间切换、下拉刷新、加载失败与重试；
  - 转账自动建议、期初唯一性、编辑/删除；
  - 交易页按类型/机构/标的筛选、已选条件标签与清除；
  - 设置页开启局域网数据服务，在电脑浏览器查看/编辑，关闭后不可访问；
  - 首页/图表与 Web 页面在中文与不同屏幕尺寸下排版正常。

### 9.4 测试数据

- JVM 测试使用 `app/src/test/.../TestFixtures.kt` 的内联 fixtures（金额为 mock 值）。
- 手工调试数据为 `debug/mock-portfolio.json`，由开发者复制到手机 `Documents/folio-prism/folio-prism.json`；不使用真实持仓作为调试数据。
- 性能测试（1 万条事件）尚未建立基准文件。

---

## 10. 实施里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 工具链（JDK/SDK/Gradle）、空 Compose 工程、安装到 Redmi | 已完成 |
| M1 | domain 模型、事件回放、估值、再平衡、JSON 持久化 + 单测 | 已完成 |
| M2 | 设置：机构/标的/类别规则/黄金加价 | 已完成 |
| M3 | 当前行情首选适配器 + 启动门禁 + 调试页（含手工价格） | 已完成 |
| M4 | 首页总资产/7 类卡片/再平衡/自适应范围条/持仓展开/黄金单克价 | 已完成 |
| M5 | 交易录入/详情/编辑/删除（六类事件）+ 转账自动建议 | 已完成 |
| M6 | 历史行情适配器 + 缓存/退避 + 7 个图表 + 下拉刷新 | 已完成 |
| M7 | 局域网 Web 工作台（概览/图表/流水编辑） | 已完成 |
| M8 | 真实数据录入方案；1 万条事件性能基准 | 未开始 |

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 只使用首选行情源，单点失败概率高 | 启动失败、无法估值 | 已确认不使用备选源；调试页展示错误；手工价格兜底；同源固定重试 1 次；后续可替换适配器 |
| 历史行情源不可用（美股/黄金等） | 曲线/盈亏缺口 | 每类只配一个首选源；缓存 + 最近收盘价回退 + 已知价格点插值；失败指数退避；缺失时标记「数据不完整」 |
| 固定路径 + MANAGE_EXTERNAL_STORAGE 在 HyperOS 上的授权/写入行为 | 无法读写数据 | 在 Redmi 14C 上验证；未授权显示权限说明页；直接文件 I/O 简化原子写入 |
| 不阻止负持仓/负现金 | 用户误操作导致数据异常 | 按 D-25 由用户负责；计算引擎处理负值不崩溃；备份可恢复 |
| 双口径/再平衡公式复杂，易回归 | 首页数字错误 | 以 portfolio.html 为黄金基准写单测；公式集中在 domain；UI 不重复实现 |
| 多币种/汇率精度 | 总资产偏差 | BigDecimal；只使用当前汇率；不保存历史汇率；CNH v1 按 1:1 |
| 修改标的类别会改变历史统计归属 | 用户困惑 | 文档说明；操作前可借助备份回滚（当前 UI 无确认提示） |
| 1 万条事件的 JSON/回放性能 | 启动/操作变慢 | 主数据与缓存分离；先测量再优化；当前未建立性能基准 |
| 转账双数量/损耗语义复杂 | 录入/计算错误 | 表单自动建议；损耗内嵌字段并有单测覆盖；编辑页只做格式校验 |
| 手工编辑 JSON 导致格式错误 | 启动失败 | 严格校验 + 加载失败页错误信息 + 备份可手工恢复；README 提示关闭 App 后编辑 |
| 局域网 Web 工作台无鉴权 | 同网段设备可读写数据 | 只在设置页手动开启；界面提示仅用于可信局域网；不做端口转发/公网暴露 |
| 仓储无写锁（App 与 Web 并发写） | 事件丢失更新 | 运行 Web 服务时避免在手机端编辑；后续可在仓储层加串行化 |
| 用户无 Android 开发经验 | 构建/安装受阻 | README 提供构建/安装步骤；开发方执行构建；手机上只需授权/安装 |
