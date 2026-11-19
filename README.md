# folio-prism

个人投资组合管理 Android App。显示名：**资产配置**。

用一个 App 替代现有的两套工具：

- invest-folio (私有 repo)：单文件 HTML 的实时估值 + 资产类别再平衡工具；
- [随手记](http://www.sui.com)：投资流水记账。

## 文档

- [需求文档](docs/REQUIREMENTS.md)
- [设计文档](docs/DESIGN.md)

## 功能概览

本地优先的个人资产配置 App：记录投资流水，对各账户资产实时估值，并按 7 大类（红利、成长、美股、中债、美债、黄金、现金）的目标区间提供再平衡计算器。

- **事件账本**：按「机构 × 标的 × 7 大类」记录转账/买卖、现金分红/利息、外部出入金、支出、期初建仓与调整；按日分组的紧凑流水，支持新增、编辑、删除与类型筛选；自动记录转账损耗与手续费支出；每种持仓只允许一笔期初。
- **实时估值与再平衡**：首页一屏展示 7 大类的当前占比、目标值与区间条，红利/组合双口径统计；各类别给出再平衡阈值价；类别卡片可展开持仓明细，美元资产同时显示 USD 与 CNY 等值。
- **价格门禁**：启动时任一必需价格获取失败会进入调试页，可刷新行情、保存日志、设置手工价格后重试，避免以错误价格展示资产。
- **本地优先的数据**：数据固定保存在手机 `Documents/folio-prism/`，原子写入 + 滚动备份；App 关闭时也允许直接编辑 JSON。

另外，提供 **局域网 Web 工作台服务**：在设置页开启服务后，同一局域网内的电脑浏览器可直接访问手机内置的 Web 服务——查看概览与图表、筛选并编辑流水，修改会写回手机上的数据文件。页面与图表库完全内置，不依赖外网，无需在电脑上安装任何软件。该服务不做鉴权，仅适合在可信局域网内使用。

当前状态：核心功能可用，178 个 JVM 单元测试覆盖计算、存储与 Web API。

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
- 历史价格：东财 pingzhongdata（基金）、腾讯 kline（ETF/债券）、新浪财经美股日线、新浪上海金期货 AU0（作为 AU9999 历史的近似）。不抓取历史汇率；历史统计中的外币资产用当前汇率折算。
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