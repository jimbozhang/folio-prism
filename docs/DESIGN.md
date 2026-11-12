# folio-prism 设计文档

- 状态：已评审，实现中
- 版本：0.2
- 日期：2026-11-12
- 关联文档：[需求文档](REQUIREMENTS.md)

本文档描述实现方案。需求层面的「为什么」见 `REQUIREMENTS.md`；本文档聚焦「怎么做」。技术实现细节可按实现情况调整，但不得违反需求文档中的可观察行为。

---

## 1. 技术选型

| 项 | 选择 | 说明 |
|---|---|---|
| 语言 | Kotlin | Google 官方 Android 开发语言；纯 Kotlin 计算核心可在 JVM 单测 |
| UI | Jetpack Compose + Material 3 | 原生 Android，适合仪表盘/图表；不做 WebView |
| 导航 | Navigation Compose | 单 Activity + Composable 页面 |
| 异步 | Kotlin Coroutines + Flow | 行情并发、启动门禁、状态流 |
| JSON | kotlinx.serialization | 纯文本、schemaVersion、BigDecimal 字符串序列化 |
| 网络 | OkHttp | 直接调用公开行情接口；不用 Retrofit 以减少抽象 |
| 图表 | Vico（折线/柱状）+ Compose Canvas（横向条形、区间条） | Vico 为 Compose 原生图表库；分布图沿用 invest-folio 的横向条形风格 |
| 存储 | 固定目录 `Documents/folio-prism/` 下的单个 JSON 文件 | 不使用 SQLite/Room；数据规模小，纯文本可读、易备份 |
| 存储权限 | `MANAGE_EXTERNAL_STORAGE` | Android 11+ 直接读写公共 Documents 目录；不提供目录选择 |
| 依赖注入 | 手工组合根 | App 规模小，不引入 Hilt/Koin |
| 构建 | Gradle Wrapper + Android SDK cmdline-tools | JDK 17；debug APK；自用不上架 |
| minSdk | 26 | 覆盖两台目标机；Redmi 14C 出厂 Android 14 |
| 显示名 | 投资组合 | 已确认 |
| applicationId | `com.jimbozhang.folioprism` | 已确认 |

**不引入**：Room、Retrofit、Hilt、WebView、Flutter、React Native、SAF 目录选择。

### 1.1 工程结构

单 Gradle module（`:app`），按包分层：

```
app/src/main/java/com/jimbozhang/folioprism/
  domain/         纯 Kotlin，无 Android 依赖
    model/        实体与事件
    calc/         估值、再平衡、盈亏、事件回放、统计
    money/        Decimal 工具
  data/
    json/         序列化、schema 迁移、校验
    store/        PortfolioStore（内存状态 + 持久化）
    price/        当前行情适配器、历史行情适配器、价格缓存
    backup/       原子写入、备份、恢复
    log/          日志环形缓冲与文件输出
  platform/
    storage/      固定目录、MANAGE_EXTERNAL_STORAGE 检查、文件读写
    network/      OkHttp 客户端
  ui/
    home/ charts/ transactions/ settings/ debug/
    components/   复用组件（区间条、分布条、金额格式化等）
    theme/
  MainActivity.kt
```

`domain` 包必须能作为纯 JVM 代码测试；Android 相关能力通过接口注入。

---

## 2. 总体架构

### 2.1 核心对象

- `PortfolioStore`：唯一数据源。持有内存中的 `PortfolioData`（settings/institutions/instruments/events），暴露 `StateFlow<PortfolioState>`；所有修改通过它完成并触发持久化。
- `CalculationEngine`：纯函数。输入事件 + 价格 + 汇率，输出持仓、类别市值、总资产、再平衡、盈亏、曲线、累计投入/取出、费用统计。
- `QuoteService`：当前行情。按「标的 → 首选数据源适配器」抓取，返回 `QuoteResult` 或 `QuoteFailure`。启动门禁由它驱动。
- `HistoricalPriceService`：历史行情。按区间按需抓取、合并缓存、提供日序列。
- `PriceCache`：历史价格缓存文件（独立 JSON）。
- `DataRepository` / `BackupService`：JSON 读写、原子提交、备份、恢复。
- `LogService`：内存环形缓冲 + 文件输出，供调试页使用。

### 2.2 启动流程

```
启动
  → 检查「所有文件访问」权限（MANAGE_EXTERNAL_STORAGE）
      → 未授予：跳转系统授权页；拒绝则显示调试页说明
  → 确保 Documents/folio-prism/ 存在
  → 读取 folio-prism.json
      → 缺失：创建空数据（预置 7 类规则）→ 空状态首页
      → 损坏：调试页（可恢复备份）
  → 构建 PortfolioStore（回放事件得到持仓）
  → QuoteGate.fetchRequiredQuotes()
      → 并发抓取所有必需当前价格 + 汇率（仅首选源）
      → 全部成功？
          → 是：写入当日价格到历史缓存（best effort）
                 → 计算结果 → 首页
          → 否：调试页（失败列表、日志、刷新、保存日志、手工价格）
```

启动期间显示加载页，不展示总资产。**任何必需价格失败都不进入首页**。价格缓存不参与门禁判断。

### 2.3 刷新流程

- 首页下拉刷新触发 `QuoteGate.refresh()`。
- 成功：更新内存报价、重算、写入当日缓存、刷新首页。
- 失败：进入全屏调试页，直到刷新成功；不得用上一次的内存价格继续展示总资产（已确认 Q-04）。
- 不实现后台定时刷新。

### 2.4 状态与并发

- 行情请求使用 `coroutineScope` 并发，限制并发数（如 6），每个请求独立超时（默认 8s），不自动切换备选源；可选 1 次同源重试（用户可配置）。
- 同一标的被多个机构持有时只请求一次（按 instrumentId 去重）。
- 事件修改与行情刷新通过 `Mutex` 串行化写入，避免并发写文件。
- UI 状态通过 `StateFlow` 暴露；Composable 只订阅状态，不直接做计算。

---

## 3. 数据模型

### 3.1 主数据文件 `folio-prism.json`

示例（省略部分事件）：

```json
{
  "schemaVersion": 1,
  "meta": {
    "createdAt": "2026-11-12T10:00:00+08:00",
    "updatedAt": "2026-11-12T10:30:00+08:00",
    "appVersion": "0.1.0"
  },
  "settings": {
    "baseCurrency": "CNY",
    "categories": [
      {
        "id": "cat_dividend",
        "name": "红利",
        "order": 1,
        "color": "#ff7675",
        "targetPct": "32",
        "minPct": "28.8",
        "maxPct": "35.2",
        "specialRule": "dividend"
      },
      {
        "id": "cat_cash",
        "name": "现金",
        "order": 7,
        "color": "#b2bec3",
        "targetPct": "12",
        "minPct": "2",
        "maxPct": "22",
        "specialRule": null
      }
    ],
    "gold": {
      "defaultSource": "XAU",
      "institutionPremiumsCnyPerGram": {
        "inst_boc": "0",
        "inst_icbc": "-0.88",
        "inst_bocom": "0.24",
        "inst_pboc": "0.0333"
      }
    },
    "backup": { "retainCount": 5 },
    "quote": { "timeoutMs": 8000, "retryCount": 1, "maxConcurrency": 6 },
    "charts": { "defaultRange": "YTD" }
  },
  "institutions": [
    {
      "id": "inst_bocom",
      "name": "交通银行",
      "type": "bank",
      "chartAlias": null,
      "notes": ""
    },
    {
      "id": "inst_pboc",
      "name": "人民银行",
      "type": "bank",
      "chartAlias": "熊猫金币",
      "notes": ""
    }
  ],
  "instruments": [
    {
      "id": "ins_008163",
      "categoryId": "cat_dividend",
      "code": "008163",
      "name": "南方标普红利低波50ETF联接A",
      "currency": "CNY",
      "unit": "份",
      "kind": "quoted",
      "quote": {
        "mode": "auto",
        "source": "auto",
        "manualPrice": null
      },
      "precision": { "priceDecimals": 4, "quantityDecimals": 2 },
      "currencyExposure": "auto",
      "notes": ""
    },
    {
      "id": "ins_cny_cash",
      "categoryId": "cat_cash",
      "code": "CNY",
      "name": "人民币现金",
      "currency": "CNY",
      "unit": "元",
      "kind": "cash",
      "quote": { "mode": "none", "source": "none", "manualPrice": null },
      "precision": { "priceDecimals": 2, "quantityDecimals": 2 },
      "currencyExposure": "auto",
      "notes": ""
    }
  ],
  "events": [
    {
      "id": "evt_0001",
      "type": "opening",
      "date": "2026-01-01T00:00:00+08:00",
      "createdAt": "2026-11-12T10:00:00+08:00",
      "position": { "institutionId": "inst_bocom", "instrumentId": "ins_008163" },
      "quantity": "51384762.95",
      "price": "0.9637",
      "fxToCny": "1",
      "valueCny": "49519496.05",
      "note": "期初持仓（示例）"
    },
    {
      "id": "evt_0002",
      "type": "transfer",
      "date": "2026-03-05T14:30:00+08:00",
      "createdAt": "2026-11-12T10:05:00+08:00",
      "from": { "institutionId": "inst_bocom", "instrumentId": "ins_cny_cash" },
      "to": { "institutionId": "inst_bocom", "instrumentId": "ins_008163" },
      "fromQuantity": "1319836.49",
      "toQuantity": "1284637.42",
      "fromPrice": null,
      "toPrice": "1.0274",
      "fromFxToCny": "1",
      "toFxToCny": "1",
      "fromValueCny": "1319836.49",
      "toValueCny": "1319836.49",
      "lossValueCny": "0",
      "note": "买入（示例）"
    },
    {
      "id": "evt_0003",
      "type": "income",
      "kind": "dividend",
      "date": "2026-06-20T10:00:00+08:00",
      "createdAt": "2026-11-12T10:06:00+08:00",
      "source": { "institutionId": "inst_bocom", "instrumentId": "ins_008163" },
      "destination": { "institutionId": "inst_bocom", "instrumentId": "ins_cny_cash" },
      "amount": "847263.58",
      "currency": "CNY",
      "fxToCny": "1",
      "amountCny": "847263.58",
      "immediateExternalWithdrawal": false,
      "externalAccountLabel": null,
      "linkedEventId": null,
      "note": "现金分红（示例）"
    },
    {
      "id": "evt_0004",
      "type": "expense",
      "date": "2026-06-20T10:00:00+08:00",
      "createdAt": "2026-11-12T10:07:00+08:00",
      "position": { "institutionId": "inst_bocom", "instrumentId": "ins_cny_cash" },
      "amount": "1364.27",
      "currency": "CNY",
      "fxToCny": "1",
      "amountCny": "1364.27",
      "categoryId": "cat_cash",
      "linkedTransferId": null,
      "note": "手续费（示例）"
    },
    {
      "id": "evt_0005",
      "type": "external",
      "direction": "out",
      "classification": "consumption",
      "date": "2026-07-01T09:00:00+08:00",
      "createdAt": "2026-11-12T10:08:00+08:00",
      "position": { "institutionId": "inst_bocom", "instrumentId": "ins_cny_cash" },
      "amount": "2163847.19",
      "currency": "CNY",
      "fxToCny": "1",
      "amountCny": "2163847.19",
      "counterpartyLabel": "日常消费账户",
      "note": "领出（示例）"
    }
  ]
}
```

### 3.2 实体定义

#### Category（固定 7 类）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 固定 ID，不可增删 |
| name | string | 默认 红利/成长/美股/美债/中债/黄金/现金；可改名 |
| order | int | 展示顺序 |
| color | string | 图表颜色 |
| targetPct | decimal string | 目标比例（百分数，如 `"32"`） |
| minPct / maxPct | decimal string | 允许区间 |
| specialRule | `"dividend"` \| null | 红利类使用 totalValue 基数；其余为 null |

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
| categoryId | string | 7 大类之一；用户可自由分配（如 000015 → 现金） |
| code | string | 008163、VOO、AU9999、CNY、USD 等 |
| name | string | 显示名称 |
| currency | string | `CNY` / `USD` / `HKD` / `CNH` |
| unit | string | 份/股/克/元/张 |
| kind | `cash` \| `quoted` | 现金类（数量即金额）与有净值类 |
| quote.mode | `auto` \| `manual` \| `none` | `none` 仅用于 CNY 现金 |
| quote.source | string | `auto` 或显式适配器名（`eastmoneyFund`、`tencentEtf`、`tencentUs`、`au9999`、`xau`、`fx` 等） |
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
| createdAt / updatedAt | ISO 8601 string | 审计时间 |
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
| fromFxToCny | 转出币种 → CNY 的汇率（CNY=1；CNH v1=1） |
| toFxToCny | 转入币种 → CNY 的汇率 |
| fromValueCny | 转出价值（CNY），保存时计算并写入 |
| toValueCny | 转入价值（CNY），保存时计算并写入 |
| lossValueCny | `fromValueCny − toValueCny`；可为正（损耗）或负（收益） |
| note | 备注 |

计算：

```
fromNativeValue = fromQuantity × (fromPrice ?: 1)   // 现金标的 price 视为 1
toNativeValue   = toQuantity   × (toPrice ?: 1)
fromValueCny    = fromNativeValue × fromFxToCny
toValueCny      = toNativeValue   × toFxToCny
lossValueCny    = fromValueCny − toValueCny
```

- 默认 `fromValueCny == toValueCny`；允许不同。
- 当 `lossValueCny != 0` 时，App 自动生成一笔关联的「损耗支出」事件（`expense`，`position=null`，`amountCny=lossValueCny`，`linkedTransferId=transfer.id`），并按其类别归属规则设置 `categoryId`：
  - 若 only 一侧为现金标的：归属于非现金一侧的类别；
  - 若两侧都是非现金：归属于转出侧类别；
  - 若两侧都是现金：归属于现金类别。
- 用户可编辑/删除自动生成的损耗支出；删除后转账仍保留 `lossValueCny`，但类别盈亏不再扣减（数据正确性由用户负责）。
- 同一标的跨机构搬仓：两侧都是 quoted，`fromQuantity=toQuantity`、`fromPrice=toPrice`，`lossValueCny=0`。
- 基金转换：拆成卖出 + 买入两笔转账（允许同一天），不设单独事件。
- 不设 `fee` 字段；手续费使用独立的 `expense` 事件（FR-TX-03）。

#### income（收益）

| 字段 | 说明 |
|---|---|
| kind | `dividend` / `interest` |
| source | 来源持仓；股息必填，利息可为 null |
| destination | 入账现金标的持仓（必须是 `kind=cash`） |
| amount | 收益金额（原币） |
| currency | 收益币种 |
| fxToCny | 币种 → CNY 汇率 |
| amountCny | 折算后的 CNY 金额（保存时写入） |
| immediateExternalWithdrawal | 是否立即转出到外部消费账户 |
| externalAccountLabel | 外部账户标签（如「日常消费账户」） |
| linkedEventId | 若立即转出，关联的外部出金事件 ID |

语义：

- 分红全部为现金分红：现金增加，源持仓数量不变；净值下跌由行情体现。
- 不支持再投资；再投资由用户手工录入一笔买入转账。
- 利息：source 可为空（银行利息）；destination 为现金标的。
- `immediateExternalWithdrawal=true` 时，App 生成一笔关联的外部出金（`external`，`direction=out`，`classification=consumption`），金额等于到账金额；双向 `linkedEventId`。
- 分红/利息不减少源持仓数量。
- 保存时写入 `amountCny`、`fxToCny`；如能取到来源标的当日价格，也写入 `sourcePrice`（用于快照/审计）。

#### expense（支出）

| 字段 | 说明 |
|---|---|
| position | 可选；若为现金标的持仓，则真实扣减该现金余额；若为 null，表示转账损耗等非现金支出 |
| amount | 支出金额（原币），**可以为负值**（返还/收益） |
| currency | 币种 |
| fxToCny | 币种 → CNY 汇率 |
| amountCny | 折算后的 CNY 金额（保存时写入） |
| categoryId | 盈亏归属类别（手续费/损耗计入哪个类别） |
| linkedTransferId | 若由转账差额自动生成，关联转账 ID |
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
| fxToCny / amountCny | 汇率与 CNY 金额（保存时写入） |
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
| fxToCny | 币种 → CNY 汇率（可选快照） |
| valueCny | 期初 CNY 价值（可选快照） |
| date | 期初时点 |

- 期初事件本身不产生盈亏，也不产生资金流；它定义回放的起点。
- 同一持仓允许只有一条期初事件；重复会提示（不阻止，用户自行负责）。

#### adjustment（调整/平账）

| 字段 | 说明 |
|---|---|
| position | 机构 × 标的 |
| deltaQuantity | 有净值标的的数量增减（可为负） |
| deltaAmount | 现金标的的金额增减（可为负） |
| price | 事件日价格快照（可选） |
| fxToCny | 汇率快照（可选） |
| valueCny | 调整的 CNY 价值（可选） |
| reason | 原因（必填） |
| date | 时间 |

- 用于数据对账修正；**按资金流处理，不计入盈亏**，避免修正产生虚假收益。
- 允许出现负持仓/负现金，不阻止、不提示（D-25）；计算引擎需能处理负值。

#### 事件排序与回放

- 排序键：`date` → `createdAt` → `id`。
- 回放范围：从最早事件到目标日期（含当日）。
- 持仓状态 = 期初 + 转账 + 收益 + 支出 + 外部 + 调整的累积。
- 允许负持仓/负现金；UI 不阻止；计算按负值继续。
- 删除/编辑事件后重新回放；1 万条事件规模需保持可用（见 NFR-03）。

### 3.3 历史价格缓存 `price-cache.json`

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-11-12T10:30:00+08:00",
  "quotes": {
    "ins_008163": {
      "currency": "CNY",
      "auto": {
        "2026-01-02": "0.9810",
        "2026-01-05": "0.9852"
      },
      "manual": {
        "2026-03-31": "1.0200"
      },
      "lastAutoFetchAt": "2026-11-12T10:30:00+08:00"
    }
  },
  "fx": {
    "USDCNY": {
      "auto": { "2026-01-02": "7.0010" },
      "manual": {}
    },
    "HKDCNY": { "auto": {}, "manual": {} }
  }
}
```

- 读取优先级：`manual` > `auto`。
- 历史估值优先使用该文件；当前估值不使用该文件（FR-START-07）。
- 当前价格成功后，把当日价格写入 `auto`（best effort）。
- 缓存损坏时可丢弃并重新抓取；不阻塞启动（缓存不是当前估值来源）。
- 缓存文件与主数据同目录，用户可用文件管理器备份。
- CNH 在 v1 视作 CNY，不需要 `CNHCNY` 缓存。

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
- 美元计价资产：显示 USD 价值 + CNY 等值；人民币计价资产：只显示 CNY，不显示 USD 等值。
- 类别汇总：CNY 口径；若类别内存在美元计价资产，额外显示美元资产小计（原币）及其 CNY 等值，不把整个类别折算为 USD。
- 价格/汇率缺失时，`valueCNY = null`（历史统计中标记缺口）；当前估值缺失则启动门禁失败。

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
  - `visualPct = catValue / totalValue`；
  - `visualTarget = targetPct × catBase / totalValue`，其中 `catBase` 红利为 `totalValue`、其他为 `portfolioValue`；
  - min/max 同理；
  - 保留 invest-folio 的缩放逻辑（取最大值 ×1.15，上限 100%）。

```
dynAmount(p, base, catValue) = p * (base - catValue) / (1 - p)   // p 为小数
```

- `dynAmount` 在 `p >= 1` 时返回无穷；UI 显示 `--`/`∞`，不得崩溃。
- 缺口：
  - `diffTarget = catValue - targetAmount`
  - `diffMin = catValue - minAmount`
  - `diffMax = catValue - maxAmount`
  - 低于目标时显示下限缺口（`diffMin`），高于目标时显示上限缺口（`diffMax`），恰好等于目标显示持平；保留 invest-folio 行为。

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

### 4.4 盈亏（v1）

#### 定义

对区间 `[t0, t1]`：

```
P&L_total = V_total(t1) - V_total(t0) - externalCapitalIn + externalCapitalOut
P&L_category(c) = V_c(t1) - V_c(t0) - netCapitalFlowInto(c) - transferLoss(c) - cashExpenseAttribution(c)
```

其中：

- `V(t)` 使用历史收盘价/净值/汇率估值；缺失时按 4.5 用事件快照近似。
- `externalCapitalIn/Out` 只包括 `external` 事件中 `capital` 与 `consumption` 的分类；它们都是本金流，不计盈亏。
- `netCapitalFlowInto(c)` 包括：
  - 跨类别转账：来源类别按 `fromValueCny` 流出，目标类别按 `toValueCny` 流入；
  - 分红/利息（有 source）：视为跨类别转账，来源类别流出 `amountCny`，现金类别流入 `amountCny`；
  - 外部本金入金/出金：记入现金类别；
  - 调整事件：按事件日价格/金额折算后，计入对应类别（视为资金流）。
- **不计入资金流**：
  - 外部利息（`income`，source=null）：已通过现金余额增加计入现金类别盈亏；
  - 支出（`expense`）：现金支出已通过现金余额减少计入盈亏；非现金损耗（position=null）通过 `transferLoss(c)` 扣除；
  - 同类别内部的转账/分红：类别市值变化自然反映，按 0 净流处理；
  - 再投资：由用户手工录入买入转账，按转账处理。
- `transferLoss(c)`：自动生成的损耗支出（`expense`，position=null）按 `categoryId` 汇总；正值扣减盈亏，负值增加盈亏。
- `cashExpenseAttribution(c)`：现金支出事件若 `categoryId` 不是现金类别，则在类别盈亏中把金额从现金类别转移到目标类别（总盈亏不变）；实现上：
  - 现金类别 P&L 加上 `amountCny`（抵消现金减少）；
  - 目标类别 P&L 减去 `amountCny`。
- 分红/利息在支付瞬间是「来源持仓净值下降 + 现金增加」的转移，类别合计盈亏为 0；不代表没有收益，而是收益已通过净值/现金体现。v1 不单列「分红收入」，后续迭代再加。

#### 汇率

- 估值使用目标日的历史汇率（缺失时用最近可用汇率，标记）。
- 资金流按事件发生日的汇率折 CNY；事件中已保存 `fromValueCny`/`toValueCny`/`amountCny`，优先使用事件快照，保证可复现。
- CNH v1 按 1:1 CNY。

#### 历史价格与缺口

- 收盘价/净值按日序列提供；周末/节假日沿用最近交易日收盘价。
- 缺失交易日：该日估值缺口；若区间端点缺失，类别盈亏标记「数据不完整」，不输出伪造数字。
- 手工补录价格优先于自动价格。
- 若历史缓存/数据源不可用，按 4.5 用事件快照计算近似值，并标记「近似」。

### 4.5 事件快照与近似回退

每笔事件保存了当时的价格/汇率与 CNY 价值（见 3.2）。这保证即使历史行情缓存损坏或数据源失效，也能：

1. **审计**：查看每笔交易发生时的价格、汇率与价值。
2. **近似盈亏**：对区间 `[t0, t1]`，若缺少历史日估值：
   - 找到每个持仓在 `t0` 之前最近的事件价格快照，以及 `t1` 之前最近的事件价格快照（或当前价格）；
   - 用这些快照估算 `V(t0)`、`V(t1)`；
   - 资金流直接使用事件中保存的 CNY 金额；
   - 结果标记「近似（基于事件快照）」。
3. **交易间盈亏**：对已实现的部分，直接用转出/转入事件中的价值计算；对未平仓部分，用最近事件价格或当前价格估算。

近似算法必须保守、可解释；不得把近似结果伪装成精确值。

### 4.6 总资产曲线

- 对每个自然日 `d ∈ [start, end]`：
  - 回放事件到 `d` 结束；
  - 对每个持仓取 `d` 的收盘价/净值（非交易日取最近交易日）；
  - 计算 `V_total(d)`。
- 曲线点若某持仓缺价：该点标记缺口（折线断开），不填 0。
- 默认范围：今年至今；可选月初至今、近 1 月、近 3 月、近 1 年、全部、自定义。
- 数据来源：`HistoricalPriceService` + `price-cache.json`；缺失时可用事件快照近似并标记。

### 4.7 累计投入/取出

- 数据来自 `external` 事件：
  - 累计投入：`direction=in && classification=capital` 的金额（CNY）按时间累加；
  - 累计取出：`direction=out && (classification=capital|consumption)` 的金额按时间累加；
  - 净投入 = 累计投入 − 累计取出。
- 不依赖历史行情，始终可计算。
- 时间范围与总资产曲线一致；按日/月聚合。

### 4.8 费用统计

- 数据来自 `expense` 事件：
  - 现金手续费（position≠null）与非现金转账损耗（position=null）都计入；
  - 负值支出（返还/收益）参与抵扣；
  - 按月份、类别（`categoryId`）、支出类型分组；
  - 汇总金额使用 `amountCny`。
- 不依赖历史行情，始终可计算。

### 4.9 机构/币种分布

- 机构分布：按 `institutionId` 汇总当前 `valueCNY`；显示 `chartAlias ?: name`。
- 币种分布：
  - 默认：`currencyExposure=auto` 时按标的 `currency`；CNH 归入 CNY；
  - 黄金默认归入 CNY；用户可把某黄金标的设为 `PHYSICAL_GOLD`；
  - 现金标的按 `currency`；
  - 支持覆盖为 `CNY/USD/HKD/PHYSICAL_GOLD`。
- 占比分母为 `totalValue`。

### 4.10 精度

- 使用 `BigDecimal`；金额、数量、价格、比例均以十进制字符串序列化。
- 计算中间结果保留至少 8 位小数；展示按标的/币种精度四舍五入。
- 禁止 `Double` 参与金额累计；图表绘制可用 `Float`，但必须先由 `BigDecimal` 计算完成。
- 百分比展示：默认 1 位小数；目标比例按设置原样展示。

---

## 5. 行情系统

### 5.1 原则

- **当前行情只使用首选数据源**：沿用 `portfolio.html` 中每个标的类型对应的首选源；不使用备选源。首选源失败即视为行情获取失败（D-11/FR-QUOTE-01）。
- **历史行情**使用每类数据的一个首选源；失败时显示缺口/允许手工补录，不自动切换备选源（FR-HIST-03/04）。
- 网络请求不携带任何持仓金额；只携带标的代码。
- 所有请求设置 UA/Referer（按各接口需要）；不使用需要密钥的接口。

### 5.2 标的类型与路由

创建标的时按以下规则推断 `quote.source`（可在设置中覆盖）：

| 条件 | 类型 | 当前行情首选源 |
|---|---|---|
| `kind=cash` 且 currency=CNY/CNH | 人民币现金 | 无需请求，price=1 |
| `kind=cash` 且 currency=USD/HKD | 外币现金 | 汇率适配器（USD/HKD → CNY） |
| 6 位数字，类别=中债 或代码以 51 开头 | 场内 ETF/债券 | Tencent kline 现价 |
| 其余 6 位数字 | 场外基金 | Eastmoney 基金批量接口 |
| 2-5 位大写字母 | 美股/美股 ETF | Tencent US |
| `AU9999` | 黄金 | 用户在 AU9999 与 XAU 中选择的默认源（只用一个） |
| 其他 | 未识别 | 手工价格（`quote.mode=manual`） |

- 同一标的只取一次；多个机构持仓共享结果。
- 手工价格视为有效价格源，不触发启动门禁失败。

### 5.3 当前行情适配器（首选源，逐条移植 portfolio.html）

1. **汇率**：`https://open.er-api.com/v6/latest/USD` 获取 USD/CNY、HKD/CNY；不使用 Eastmoney fallback。CNH v1 视作 CNY。
2. **场外基金**：Eastmoney 基金批量接口（Fcodes 逗号分隔）；ETF 用 `NEWPRICE`，基金用 `NAV`；同源最多重试 1 次（可配置）。不使用 fundgz/pingzhongdata 作为当前价格 fallback（pingzhongdata 仅用于历史净值）。
3. **场内 ETF/债券**：Tencent `web.ifzq.gtimg.cn/appstock/app/fqkline/get`，取 `qt[3]`；不使用 Eastmoney fallback。
4. **美股/ETF**：Tencent `us{SYMBOL}`，取 `qt[3]`；盘前/盘后 `pandata.last` 覆盖收盘；不使用 Eastmoney fallback。
5. **黄金**：用户在设置中选择默认源：
   - AU9999：Eastmoney push2 `118.AU9999`（CNY/克）；
   - XAU：`https://api.gold-api.com/price/XAU`（USD/盎司 ÷ 31.1035 × USD/CNY）。
   - 只使用选中的源；失败即失败。每个机构加价在估值时叠加。
6. **手工价格**：直接使用 `quote.manualPrice`，不发起网络请求。

实现注意：

- `pingzhongdata` 返回 JS 文本，仅用于历史净值解析，需要在 Kotlin 中按文本正则/字符串解析。
- 端点可能随时变化；每个适配器必须返回结构化错误（source、url 概要、异常），供调试页展示。
- 精度：基金净值 4 位、ETF 3 位、美股 2 位、黄金 2 位、汇率 6 位；具体按接口返回和标设置。

### 5.4 启动价格门禁

1. 从当前持仓中收集所有 `quantity > 0` 的 `(instrument, institution)`。
2. 对每个 `instrument` 生成必需报价：
   - `kind=cash`：若 currency=CNY/CNH → 本地 price=1；否则需要对应 FX。
   - `kind=quoted`：`quote.mode=manual` → 本地手工价；否则需要 `quote.source` 成功（只用首选源）。
   - 黄金：需要金价；若 XAU 源还需要 USD/CNY。
3. 对每个非 CNY 币种（USD/HKD）生成必需 FX 请求。
4. 并行执行（并发上限默认 6），每请求超时 8s；同源最多重试 1 次；不切换备选源。
5. 任一必需项失败 → 构造 `StartupFailure`，携带失败列表与日志，显示调试页。
6. 全部成功 → 更新内存报价 → 写入当日价格到历史缓存（best effort，失败仅日志）→ 进入首页。

**不使用缓存**：门禁判断不读取 `price-cache.json`；也不使用上一次会话的内存价格。启动必须重新获取。

### 5.5 调试页

全屏 Composable，不能用底部导航返回主界面。

- **失败列表**：机构、标的代码/名称、价格类型（现价/汇率/金价）、首选源、错误、时间。
- **日志**：最近 N 条（如 500 条）应用日志，等宽滚动区。
- **按钮**：
  - 「刷新」：重新执行门禁；
  - 「保存日志」：写入 `<数据目录>/logs/folio-prism-<timestamp>.log`，并提示路径；
  - 「设置手工价格并重试」：为失败标的打开手工价格输入，保存到标的设置后重试（Q-01 已确认）。
- 若存储权限未授予/目录不可访问：显示「打开系统授权页」。
- 日志按行记录：时间、级别、模块、标的代码/名称、首选源、错误；**不记录金额、数量、汇率**。

### 5.6 历史行情适配器（首选源）

> 这些是新增能力，必须在目标手机上验证；PC 端测试结果不代表手机端。

| 数据类型 | 首选源（已在 Redmi 14C 上验证） |
|---|---|
| 场外基金净值历史 | `fund.eastmoney.com/pingzhongdata/{code}.js` 的 `Data_netWorthTrend` |
| 场内 ETF/债券 | Tencent kline（`fqkline/get?param={sh|sz}{code},day,...`） |
| 美股/美股 ETF | 新浪财经美股日线：`stock.finance.sina.com.cn/usstock/api/jsonp.php/.../US_MinKService.getDailyK?symbol={SYMBOL}` |
| 黄金 | 新浪财经上海金期货连续合约 AU0：`InnerFuturesNewService.getDailyKLine?symbol=AU0`；**作为 AU9999 现货历史的近似**，因为 XAU 历史源需要 API key |
| USD/CNY、HKD/CNY | Frankfurter（`api.frankfurter.app` / `api.frankfurter.dev`，ECB 参考价） |

- 东财 `push2his` 的 K 线接口在目标手机上会被断流，因此不作为历史源。
- 每类只配置一个首选源；失败时按 FR-HIST-04 处理，不自动切换备选源。
- 每个适配器返回 `Map<LocalDate, BigDecimal>`；失败时返回结构化错误。
- 同一标的的历史数据只请求一次并发区间；合并进缓存。
- 黄金历史在 UI 日志中标注 `sina AU0 futures (proxy for AU9999)`；后续若拿到可靠的 AU9999/XAU 历史源可替换。

### 5.7 历史数据获取与缺口

- 用户首次查询区间 `[t0, t1]`：
  1. 从缓存读取已有的日序列；
  2. 找出缺失的日期区间，按首选适配器抓取；
  3. 合并写入缓存；
  4. 计算曲线/盈亏。
- 非交易日：使用最近交易日的收盘价（不写缓存为交易日值）。
- 缺失交易日：曲线断点；区间端点缺失 → 盈亏标记「数据不完整」。
- 手工补录：用户可在图表页/标的详情中为某日输入价格；写入 `manual`，优先于 `auto`。
- 缓存文件损坏/丢失：丢弃并重新抓取；不影响主数据。
- 缓存写入失败：显示警告，允许重试；不阻塞当前行情与首页。
- 历史数据不可用时，按 4.5 使用事件快照近似，并标记「近似」。

---

## 6. 数据存储与备份

### 6.1 固定目录与权限

- 固定路径：`/storage/emulated/0/Documents/folio-prism/`（通过 `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS)` 解析，避免硬编码）。
- 不提供目录选择，不制作应用内启动设置页（D-21）。
- Android 11+ 需要 `MANAGE_EXTERNAL_STORAGE`（「所有文件访问」）：
  - 在 Manifest 声明；
  - 启动时检查 `Environment.isExternalStorageManager()`；
  - 未授予：通过 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 跳转系统授权页；
  - 用户拒绝：显示调试页说明原因，不写入数据。
- 使用直接文件 I/O（`java.io.File`），不使用 SAF/DocumentFile。
- 该权限适合自用 sideloaded APK；不涉及应用商店政策。

### 6.2 目录与文件

```
Documents/folio-prism/
  folio-prism.json          主数据
  price-cache.json          历史价格缓存
  backups/
    folio-prism-2026-11-12T103000.json
    price-cache-2026-11-12T103000.json
  logs/
    folio-prism-2026-11-12T103000.log
```

- README 写明路径；用户用文件管理器复制上述文件即为备份。
- App 不提供导入/导出 UI；允许在 App 关闭时手工编辑 JSON（见 6.6）。
- 如需恢复，用户可把备份文件放回目录并改名为 `folio-prism.json`；调试页也会提示可恢复的备份（不自动覆盖）。

### 6.3 主数据原子写入协议

1. 将新 JSON 序列化为字节（UTF-8，pretty print）。
2. 在目标目录写入临时文件 `folio-prism.tmp.json`，并 `flush`/`close`（必要时 `fsync`）。
3. 重新读取临时文件并解析校验（schema + 反序列化）；失败则中止，保留原文件。
4. 将当前 `folio-prism.json` 复制到 `backups/folio-prism-<timestamp>.json`（如果存在）；失败则中止。
5. 删除旧 `folio-prism.json`，将 `folio-prism.tmp.json` 重命名为 `folio-prism.json`。
6. 按 `retainCount`（默认 5）清理最旧备份。
7. 记录 `meta.updatedAt`。

**启动恢复**：

- 若 `folio-prism.json` 不存在但 `folio-prism.tmp.json` 存在且可解析 → 使用 tmp，并重命名为主文件。
- 若主文件损坏/缺失 → 调试页提示；列出 `backups/` 中最近的可用备份，供用户选择恢复。
- 若 `backups/` 中也没有有效文件 → 提示新建空数据（需用户确认，不自动覆盖）。

### 6.4 价格缓存写入

- 数据可重新抓取，损坏代价低。采用「写临时文件 + 重命名」；失败则下次重写。
- 备份频率：每天最多备份一次缓存；主数据每次提交都备份。
- 缓存文件不参与当前估值门禁。

### 6.5 Schema 迁移

- `schemaVersion` 为整数；每次不兼容变更 +1。
- 启动时若文件版本低于当前版本，先备份，再执行迁移函数链；迁移失败进入调试页，不覆盖原文件。
- 未知的新版本（文件版本高于 App）→ 调试页提示升级 App，不尝试写入。

### 6.6 手工编辑 JSON

- 用户可以在 App 关闭时用文本编辑器修改 `folio-prism.json`（D-17）。
- 启动时严格校验：JSON 语法、`schemaVersion`、必填字段、ID 引用、事件类型；错误时进入调试页并指出位置/原因，不静默修复。
- App 运行期间的外部修改可能被下一次保存覆盖；设置页与 README 提示「先关闭 App 再编辑」。
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

调试页为全屏独立路由，不显示底部导航。首次启动不设应用内设置页；仅在缺少存储权限时跳转系统授权页。

### 7.2 首页

- 顶部不设应用标题与刷新按钮；下拉刷新。
- 首页滚动后，系统返回手势/返回键先回到首页顶部；已在顶部时按返回退出 App。
- 总资产卡片：
  - `总资产：¥xxx`
  - `红利 ¥xxx xx.x%`
  - `组合 ¥xxx xx.x%`
- 资产配置总览卡片（位于总资产卡片下方）：
  - 7 行，一行一个类别，一屏即可看到全部 7 类，不需要逐张下滑；
  - 每行：类别色点 + 名称 + 目标占比；横向进度条（类别色填充、绿色区间带、min/max 灰色刻度、目标深色刻度，视觉宽度统一以 `totalValue` 为分母）；右侧当前金额与当前占比（区间内绿色、偏离区间珊瑚红）；右侧箭头；
  - 点击任意行：展开对应类别卡片并平滑滚动到该卡片；
  - 数据不完整的类别在目标占比位置显示「数据不完整」。
- 类别卡片（LazyColumn）：
  - 标题行：类别、当前金额（CNY 汇总）、目标金额、是否在区间；
  - 若类别内有美元计价资产，显示美元资产小计（原币）与 CNY 等值；
  - 区间条：min/max 区间、目标刻度、当前位置；
  - 缺口行：低于/高于目标/下限/上限的金额；
  - 黄金卡片额外显示下限/上限单价；
  - 点击展开持仓表：第一行代码/名称（单行，超长省略）；第二行机构、数量、单价（原币）、市值（原币）；美元持仓附 CNY 等值。
- 空状态：显示空状态即可，不主动引导（FR-ONBOARD-03）。

### 7.3 图表页

- 顶部为图表类型下拉菜单：`总资产` / `类别资产` / `类别盈亏` / `累计投入取出` / `费用统计` / `机构分布` / `币种分布`；不使用横向滚动的切换按钮。
- 底部区间栏（前四类图表可见）：`‹  2026.1.1 ~ 2026.11.12  ›`。左右箭头按当前区间长度平移并限制到今天；点击中间文本打开区间选择对话框。
- 区间选择对话框：
  - 快捷预设：月初至今、近 1 月、近 3 月、今年至今、近 1 年、全部；
  - 当日即为月初或年初时，月初至今/今年至今为单日区间：图表以居中的水平线+单点展示，区间盈亏为 0，不报错；
  - 开始日期/结束日期分别打开 Material 日期选择器，既可在日历中选择，也可切换到输入模式直接键入任意日期；
  - 确认后按新区间重新计算；历史行情按需抓取并复用缓存。
- **总资产**：折线图（Compose Canvas）+ 区间盈亏大数字 + 期初/期末/曲线变化；缺失数据断点。
- **类别资产**：布局与总资产相同，但只统计 7 大类中的一类；页头下方提供类别下拉菜单（类别色点 + 名称），切换类别即时更新曲线、区间盈亏和「当前类别金额」，复用同一份历史结果，不重新请求行情。
- **类别盈亏**：7 大类列表（图标、占比、彩色进度条、盈亏金额）；表头显示合计；历史数据不足的类别标记为「数据不完整」。
- **累计投入取出**：区间净投入大数字 + 按月的投入/取出/净投入列表；不依赖历史行情。
- **费用统计**：区间支出合计 + 按月的支出与类别拆分；负值支出参与抵扣。
- **机构分布**：横向条形列表（金额、占比），按金额降序；显示别名。
- **币种分布**：同上，类别为 CNY/USD/HKD/实物黄金；CNH 归入 CNY。

### 7.4 交易页

- 顶部：标题 + 事件类型筛选下拉菜单（全部/转账/收益/外部/支出/期初/调整），不使用横向滚动的 Chip。
- 列表：按日分组（日期 + 星期 + 当日笔数），组内为白色圆角列表；每行显示类型图标、类型、机构/标的简称、金额/数量、备注（若有）；点击整行进入编辑；列表不常驻编辑/删除图标。
- 删除：在编辑页顶栏的删除按钮中二次确认后删除。
- 右下角金色扩展 FAB「记一笔」→ 全屏编辑页（参考随手记设计语言）：
  - 顶栏：返回、居中标题、金色「✓ 保存」；
  - 类型页签：转账 / 收益 / 支出 / 外部 / 期初 五个文字页签 + 「更多」下拉（调整）；金色下划线标识当前类型；
  - 金额区：大字号金额输入 + 原币/单位 + 自动折算的 CNY 等值；转账页显示转出/转入两段金额与损耗摘要；
  - 账户区：转出/转入两列显示机构与标的，中间按钮可交换两侧；点击打开底部持仓选择面板（机构 Chip + 标的列表）；
  - 价格与汇率区：按当前行情预填单价/汇率，可修改；手续费默认 0，可输入现金侧币种的手续费；
  - 时间行：日期打开日期选择器，时间胶囊打开时间选择器；
  - 备注行；底部金色保存按钮与校验错误提示。
  - **转账**：底层统一为转账；填写转出/转入持仓、转出数量、转入数量、价格、汇率、手续费（默认 0）；差额自动生成损耗支出，手续费非 0 时自动生成关联的手续费支出（手续费币种取现金一侧的币种；无现金一侧时按 CNY 计为非现金损耗）。
  - **收益**：分红/利息；来源可选；入账现金账户；金额；是否立即转出。
  - **外部入金/出金**：本金/消费。
  - **支出**：手续费/损耗；金额可为负值；选择现金账户或非现金损耗。
  - **期初**：期初持仓/现金；每种机构 × 标的组合只允许一笔期初事件，保存第二笔时拒绝并提示已有期初的日期。
  - **调整**：数量/金额调整 + 原因。
- 表单校验只做格式/必填校验；不阻止负持仓/负现金。
- 删除：不弹「会导致负值」提示；可删除关联事件，用户自行负责。
- 空状态：显示空状态即可。

### 7.5 设置页

主页面：

- **机构与标的**：入口卡片，显示机构数、标的数；点击进入独立管理页。
- **数据与备份**：固定路径、数量统计、备份保留份数。
- **类别与再平衡**：7 类目标/最低/最高、顺序、颜色；不允许增删类别。
- **黄金**：默认金价源、按机构加价。

管理入口卡片（主页面）：

- 机构：显示数量，进入机构管理页。
- 标的：显示数量，进入标的页。
- 类别与再平衡：显示 7 类数量，进入类别页。
- 黄金价格修正：右侧显示当前金价源（XAU/AU9999），进入黄金页。

机构管理（参考随手记账户管理页）：

- 独立页面，顶栏：返回 / 机构 / 金色「+」；底部导航保留。
- 按机构类型分组（银行 / 券商 / 其他）：灰色分组标题 + 数量；全宽白色列表；每行细线图标、名称、类型/图表别名/备注、chevron。
- 点击行进入编辑表单，`+` 新增；表单为大字号名称输入 + 类型/别名/备注白色分组，顶部删除、底部金色保存。
- 删除需二次确认；有事件引用时拒绝删除并显示原因；删除机构同时清理其黄金加价配置。

标的：

- 独立页面，顶栏：返回 / 标的 / 金色「+」。
- 按类别分组（类别色点 + 名称 + 数量）；全宽白色列表；每行类别图标、代码/名称、币种/单位/类型/行情方式、chevron。
- 表单为大字号名称输入 + 代码/类别/币种/单位、标的类型/行情方式/手工价格、价格精度/数量精度/币种暴露、备注；顶部删除、底部金色保存；有事件引用时拒绝删除。

类别与再平衡页：

- 独立页面，顶栏：返回 / 类别与再平衡（无新增按钮，7 类固定）。
- 分组「再平衡目标」+ 数量；全宽白色列表，每行类别图标/颜色、名称、目标/区间百分比、chevron。
- 编辑表单：大字号类别名称 + 目标/最低/最高百分比三行（右对齐 + `%` 后缀）；底部金色保存；不允许删除类别。

黄金价格修正页：

- 独立页面，顶栏：返回 / 黄金价格修正。
- 「金价源」单行：当前来源（国际金价 XAU / 上海金 AU9999），点击进入下拉表单。
- 分组「机构加价」+ 机构数量；每行机构图标/名称、`加价 ¥x/克`、chevron。
- 加价表单：大字号金额输入，单位 CNY/克，允许负数；底部金色保存。

数据与备份：固定路径 `Documents/folio-prism/`、数量统计、备份保留份数（默认 5）。

删除保护：

- 有事件引用的机构/标的不可删除（结构性引用保护）。
- 修改标的类别会改变历史统计归属；弹出警告并要求确认（这是结构性提示，不是数值正确性提示）。
- 删除类别不允许（7 类固定）。

### 7.6 调试页

见 5.5。全屏错误状态；顶部显示 App 版本与数据路径；失败列表可滚动；日志区等宽字体；按钮固定在底部（刷新/保存日志/设置手工价格）。

### 7.7 首次启动与空状态

1. 检查存储权限；未授予 → 系统授权页。
2. 创建 `Documents/folio-prism/`、`folio-prism.json`、`price-cache.json`；预置 7 类规则与颜色。
3. 直接进入空状态首页（不设应用内启动设置页，不主动引导）。

### 7.8 主题与格式化

- Material 3 light；中文 locale。
- 金额格式化：千分位、¥/$/HK$ 前缀、按精度四舍五入。
- 单项资产按原币显示；美元资产额外显示 CNY 等值。
- 百分比：1 位小数（目标比例按输入展示）。
- 颜色：沿用 invest-folio 的类别颜色（可编辑）：红利 `#ff7675`、成长 `#e17055`、美股 `#f9a825`、美债 `#0984e3`、中债 `#6c5ce7`、黄金 `#f9ca24`、现金 `#b2bec3`。
- 表格/条形图使用 Compose 组件，避免 WebView。

---

## 8. 构建与安装

### 8.1 工具链（开发机）

开发机当前没有 JDK/Android SDK。建议**用户目录安装**（无需 root；用户已授权需要时使用 sudo）：

- JDK 17（Temurin）解压到 `~/.local/opt/jdk-17`；
- Android cmdline-tools 解压到 `~/.local/share/android-sdk/cmdline-tools/latest`；
- 安装：
  ```
  sdkmanager --licenses
  sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
  ```
- `local.properties` 写 `sdk.dir=...`（不提交 git）。
- `JAVA_HOME`/`ANDROID_HOME` 写入 shell profile 或构建脚本。

若用户更愿意用系统包管理器（pacman/AUR），也可以；实现阶段确认。

### 8.2 构建与安装

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.jimbozhang.folioprism/.MainActivity
```

- debug 签名即可；不做 release 签名/上架。
- Redmi 14C 需要开启开发者选项与 USB 安装；adb 已可连接。
- 目标机 Xiaomi 17 Ultra 通过同样方式安装。

### 8.3 版本管理

- 遵循现有 `.gitignore`（Gradle/Android/IDE/keystore 等）。
- 不提交 `local.properties`、`build/`、`.gradle/`。
- 文档与代码同仓库；README 记录构建/安装/数据路径。

---

## 9. 测试策略

### 9.1 单元测试（JVM）

- **Decimal**：解析、格式化、四舍五入、精度。
- **事件回放**：期初、转账（两数量/两价值/损耗）、收益、支出（现金/非现金、负值）、外部入金/出金、调整；负持仓/负现金不崩溃。
- **估值**：现金、有净值、USD/HKD 汇率、CNH=CNY、黄金加价、XAU 换算。
- **双口径/再平衡**：用 invest-folio 的 RULES + mock 持仓做黄金用例，逐一比对 target/min/max/dynAmount/缺口/黄金阈值价。
- **盈亏**：跨类别转账、转账损耗、分红、支出、外部入金/出金、调整；验证区间盈亏与资金流分类。
- **事件快照回退**：历史价格缺失/缓存损坏时，使用事件快照计算近似盈亏并标记。
- **历史曲线**：交易日/周末/缺口场景。
- **累计投入/取出、费用统计**：按时间范围、分组、负值抵扣。
- **JSON**：round-trip、schema 迁移、损坏恢复、手工编辑校验。
- **行情路由**：代码/类别 → 首选适配器选择。
- **行情解析**：用录制的 JSON/JS 文本 fixture 测每个首选适配器；错误文本、空数据、超时。
- **启动门禁**：fake QuoteService 场景（全部成功、单项失败、FX 失败、手工价格）。
- **备份协议**：模拟临时文件/主文件/备份的崩溃恢复路径。
- **性能**：1 万条事件的回放、估值、JSON 读写基准。

### 9.2 UI/集成测试（可选）

- Compose UI 测试：首次启动权限页、空状态、首页卡片展开、录入转账/支出、调试页保存日志/手工价格。
- 使用 fake 文件系统/网络，避免依赖真实接口。

### 9.3 手工验收

- 在 Redmi 14C 与 Xiaomi 17 Ultra 上：
  - 首次启动、存储授权、空状态；
  - 断网启动 → 调试页；恢复网络 → 刷新进入；
  - 单项行情失败 → 调试页；设置手工价格 → 进入；
  - 数据文件在文件管理器中可见、可复制；
  - 手工编辑 JSON（App 关闭时）→ 启动加载/校验；
  - 首页/图表在中文与不同屏幕尺寸下排版正常；
  - 1 万条事件下的启动与操作性能。

### 9.4 测试数据

- `test/fixtures/`：
  - `portfolio_rules.json`（当前 7 类规则）；
  - `mock_portfolio.json`（覆盖所有事件类型、多币种、黄金、分红、支出、损耗、负值）；
  - `quote_*.json` / `*.js`（录制响应）；
  - `historical_prices.json`（日序列）；
  - `events_10k.json`（性能测试）。
- 金额用脱敏/随机 mock 值，避免把真实持仓写入测试。

---

## 10. 实施里程碑

> 在用户显式确认「开始实现」前，不写业务代码。以下为确认后的实施顺序。

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 | 工具链（JDK/SDK/Gradle）、空 Compose 工程、安装到 Redmi | App 能在 Redmi 14C 启动 |
| M1 | domain 模型、事件回放、估值、再平衡、JSON 持久化 + 单测 | 单测全绿；调试数据能算出首页数字 |
| M2 | 设置：机构/标的/类别规则/黄金加价 | 可增删改；数据落盘 |
| M3 | 当前行情首选适配器 + 启动门禁 + 调试页（含手工价格） | 断网/单项失败进调试页；刷新/手工价格恢复 |
| M4 | 首页总资产/7 类卡片/再平衡/持仓展开 | 与 portfolio.html 黄金用例一致 |
| M5 | 交易录入/列表/编辑/删除（转账/收益/支出/外部/期初/调整） | 事件驱动持仓；损耗支出自动生成；负值不阻止 |
| M6 | 历史行情首选适配器 + 缓存 + 7 个图表 | 历史区间可算；缺口/近似可见 |
| M7 | 调试数据端到端 + 手工验收 + 1 万条性能 | 两台设备通过验收清单 |
| M8 | 真实数据录入方案讨论/实现；README 完善 | 用户可开始录历史数据 |

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 只使用首选行情源，单点失败概率高 | 启动失败、无法估值 | 已确认不使用备选源；调试页展示错误；手工价格兜底；同源重试可配置；后续可替换适配器 |
| 历史行情源不可用（美股/CNH 等） | 曲线/盈亏缺口 | 每类只配一个首选源；缓存；手工补录某日价格；缺失时用事件快照近似并明确标记 |
| 固定路径 + MANAGE_EXTERNAL_STORAGE 在 HyperOS 上的授权/写入行为 | 无法读写数据 | 在 Redmi 14C 上优先验证；权限拒绝时调试页说明；直接文件 I/O 简化原子写入 |
| 不阻止负持仓/负现金 | 用户误操作导致数据异常 | 按 D-25 由用户负责；计算引擎处理负值不崩溃；备份可恢复 |
| 双口径/再平衡公式复杂，易回归 | 首页数字错误 | 以 portfolio.html 为黄金基准写单测；公式集中在 domain；UI 不重复实现 |
| 多币种/汇率精度 | 总资产偏差 | BigDecimal；事件日汇率落库；历史汇率缓存；CNH v1 按 1:1 |
| 类别可调整导致历史统计变化 | 用户困惑 | 修改类别时警告；文档说明会重算历史归属 |
| 1 万条事件的 JSON/回放性能 | 启动/操作变慢 | 主数据与缓存分离；回放增量优化；性能测试基准；必要时缓存派生状态 |
| 转账双数量/损耗语义复杂 | 录入/计算错误 | 表单联动计算；损耗支出自动生成并可编辑；单测覆盖买/卖/转换/换汇/搬仓 |
| 手工编辑 JSON 导致格式错误 | 启动失败 | 严格校验 + 明确错误位置 + 备份恢复；README 提示关闭 App 后编辑 |
| 用户无 Android 开发经验 | 构建/安装受阻 | 提供一键脚本与 README 步骤；开发方执行构建；手机上只需授权/安装 |

---

## 12. 已确认决议与遗留事项

### 已确认（原 Q-01 ~ Q-08）

- Q-01：调试页允许设置手工价格并重试。
- Q-02：v1 将 CNH 视作 CNY；后续迭代再支持 CNH/CNY 汇率。
- Q-03：接受历史缺口 + 手工补录。
- Q-04：刷新失败进入全屏调试页，直到刷新成功。
- Q-05：显示名「投资组合」，包名 `com.jimbozhang.folioprism`；图标后续设计。
- Q-06：预置 7 类规则与类别颜色；机构/标的由用户创建。
- Q-07：默认保留 5 份备份。
- Q-08：日志允许代码/名称，不含金额、数量、汇率。

### 遗留（不阻塞实现）

- App 图标设计。
- 美股历史行情的手机端首选源验证。
- 图表默认时间范围与颜色细节。
- 1 万条事件下的具体性能优化手段（先测量，再优化）。
