# folio-prism

个人投资组合管理 Android App。显示名：**投资组合**。

用一个 App 替代现有的两套工具：

- invest-folio (private repo)：单文件 HTML 的实时估值 + 7 大类再平衡工具；
- [随手记](http://www.sui.com)：投资流水记账。

## 文档

- [需求文档](docs/REQUIREMENTS.md)
- [设计文档](docs/DESIGN.md)

## 项目状态

[M0–M6](docs/DESIGN.md#10-实施里程碑) 的核心功能已可运行，并有 79 个 JVM 单元测试：

- 机构 × 标的 × 7 大类的事件账本（转账/买卖、现金分红/利息、外部出入金、支出、期初、调整）；
- 实时估值 + 首页再平衡（双口径、黄金阈值价、机构/币种分布）；
- 首页资产配置总览：7 大类一屏可见（当前/目标/区间条），点击类别跳转到明细卡片；
- 启动价格门禁：任一必需价格失败进入调试页，可刷新、保存日志、设置手工价格并重试；
- 首页类别卡片可展开持仓明细；美元计价资产显示 USD + CNY 等值；
- 设置页：机构、标的、类别再平衡、黄金价格修正均采用分组式管理页与表单；
- 交易页：按日分组的紧凑流水与全屏「记一笔」、新增/编辑/删除、类型下拉筛选、自动记录转账损耗与手续费支出；每种持仓只允许一笔期初；
- 图表页：总资产历史曲线、类别资产（单类别）、类别盈亏、累计投入/取出、费用统计、机构分布、币种分布；图表类型和类别均使用下拉菜单，时间区间支持任意起止日期；
- 数据固定保存在手机 `Documents/folio-prism/`，原子写入 + 5 份滚动备份；允许在 App 关闭时手工编辑 JSON。

## 数据存储

数据固定保存在：

```
/storage/emulated/0/Documents/folio-prism/
```

首次启动时若缺少「所有文件访问」权限，会跳转系统授权页。目录内维护：

```
folio-prism.json     主数据（账户、标的、事件、设置）
price-cache.json     历史价格缓存
backups/             带时间戳的自动备份（默认保留 5 份）
logs/                调试日志
```

备份通过手机系统文件管理器复制上述文件完成。App 不提供导入/导出 UI；允许在 App 关闭时直接手工编辑 JSON。

## 行情数据源

- 当前价格：沿用 `invest-folio` 的首选源（东财基金批量、腾讯 ETF/美股、东财 AU9999 / gold-api XAU、er-api 汇率），不使用备选源；首选失败即失败。
- 历史价格：东财 pingzhongdata（基金）、腾讯 kline（ETF/债券）、新浪财经美股日线、新浪上海金期货 AU0（作为 AU9999 历史的近似）、Frankfurter（汇率）。
- 历史缓存只用于历史统计，不作为当前价格的 fallback。

## 构建与测试

本机工具链安装在用户目录（无需 root）：

```bash
export JAVA_HOME=$HOME/.local/opt/jdk-17
export ANDROID_HOME=$HOME/.local/share/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

# JVM 单元测试
./gradlew :app:testDebugUnitTest

# 构建 debug APK
./gradlew :app:assembleDebug

# 安装到已授权的手机
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 目标设备

- 开发/调试机：Redmi 14C
- 日常使用机：Xiaomi 17 Ultra
- 最低 Android 版本：minSdk 26

## 许可

见 [LICENSE](LICENSE)。
