# 看戏

“看戏”是一个面向老人、供家庭内部使用的 Android 戏曲目录。家人负责从哔哩哔哩分享或粘贴公开免费视频链接，老人通过大分类、搜索、收藏和最近打开快速选戏；播放使用 B 站官方外链播放器，并始终提供“用哔哩哔哩或浏览器打开”兜底。

## 下载

最新版本：**v0.4.0**

GitHub Release（含 APK）：https://github.com/ennheng/kanxi/releases/latest

## 已实现

- 首页、找戏、收藏三项老人端导航，默认大字、高对比度和大触控区。
- 全新安装仅提供京剧、越剧、其他三个初始剧种；家人可按自家习惯新增、改名、删除和排序。
- 家人管理：添加、编辑、删除和排序戏曲；支持本地封面。
- 接收 Android `ACTION_SEND text/plain`，可从哔哩哔哩选择“分享给看戏”。
- 支持 `www.bilibili.com`、`m.bilibili.com` 和 `b23.tv`；短链接逐跳校验且最多 5 跳。
- 多 P 视频会从公开页面识别分 P 编号和标题；验证后默认将全部 P 导入为独立戏曲，也可以只挑其中一 P。每条资源按 `BVID + 分 P` 独立保存和去重。
- 戏曲合集（预设包）：内置 19 个剧种的视频清单；家人输入预设码即可一键导入为合集，老人在首页直接点合集看戏。
- 预设码导入：在“设置 → 家人管理”输入戏曲种类拼音全拼（如 `qinqiang`），即可一键导入该种类全部内置视频并自动生成合集。
- Room 本地目录、收藏、最近打开与合集，DataStore 字号与移动流量提醒。
- 安全 WebView、全屏播放、证书错误关闭、重新加载和“哔哩哔哩或浏览器”外部打开兜底。
- 不使用账号、后台、分析 SDK、未公开播放接口、视频直链、缓存或下载。

## 工具链

- JDK 17
- Android Gradle Plugin 9.2.0 / Gradle 9.4.1
- Kotlin 2.3.21（AGP 内置 Kotlin）
- compileSdk / targetSdk 36，minSdk 26（Android 8）
- Compose BOM 2026.06.00、Room 2.8.4、DataStore 1.2.1

仓库已包含 Gradle Wrapper。首次构建还需要安装 Android SDK Platform 36 和 Build Tools 36.0.0。

## 验证与构建 APK

在 PowerShell 中执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\verify.ps1
```

脚本会串行运行 JVM 单元测试、Android Lint 和 Debug APK 构建。当前仓库路径包含中文；Windows Gradle 测试工作进程会错误编码该路径，因此脚本会自动建立一个只用于构建的 ASCII 目录联接。源码和输出仍位于原仓库。

构建产物：

```text
app\build\outputs\apk\debug\app-debug.apk
```

Compose 仪器测试可在已连接设备或模拟器上运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 2026-07-12 验证记录

- JVM 单元测试：61 项通过，0 失败；Android Lint：0 error；Debug APK 构建成功。
- Compose 仪器测试：API 26、29、34、36 各 16 项通过，0 失败。
- 360dp、系统 200% 字号下检查多 P 选择列表：可纵向滚动、无横向滚动，点击分 P 后立即返回。
- 公开示例 `BV1reVJ6ZETf`：识别 57 P；默认全部导入后单条资源按 `BVID + 分 P` 保存，官方播放器地址保留对应 `p` 参数。
- B 站在线可用性不作为稳定 CI 断言；仍应在实际安装手机上复查平台页面、广告和播放器行为。

## 使用流程

1. 首次启动只有京剧、越剧、其他三个剧种，不预置真实视频；家人可在“管理剧种”中自由调整。
2. 进入“设置 → 家人管理 → 添加戏曲”，粘贴链接并验证；也可以在哔哩哔哩中直接“分享给看戏”。多 P 视频默认全部添加，也可以只挑其中一 P 保存。
3. 填写剧名并从自定义剧种列表中选择；演员、剧团、搜索别名、备注和本地封面均可选。
4. 老人从首页分类或“找戏”进入详情，第二次点击“播放”即可打开官方播放器。
5. WebView 失败时使用页面顶部的“用哔哩哔哩或浏览器打开”。

## 预设码对照表

在“家人管理”点击“导入预设”，输入下方预设码即可一键导入对应剧种视频清单：

| 预设码 | 剧种 | 预设码 | 剧种 |
|--------|------|--------|------|
| `jingju` | 京剧 | `huju` | 沪剧 |
| `qinqiang` | 秦腔 | `chaoju` | 潮剧 |
| `yuju` | 豫剧 | `huaguxi` | 花鼓戏 |
| `yueju` | 越剧 | `lvju` | 吕剧 |
| `huangmeixi` | 黄梅戏 | `yuediao` | 越调 |
| `kunqu` | 昆曲 | `puju` | 蒲剧 |
| `yueju2` | 粤剧 | `wuju` | 婺剧 |
| `pingju` | 评剧 | `shaoju` | 绍剧 |
| `chuanju` | 川剧 | | |
| `hebeibangzi` | 河北梆子 | | |
| `jinju` | 晋剧 | | |

> 说明：粤剧与越剧拼音均为 `yueju`，因此粤剧使用 `yueju2`。导入时会自动创建对应剧种分类与合集。

## 人工验收清单

- 在 API 26、29、34、36 上启动，检查 360dp 小屏、横竖屏、系统字号 100%/200%。
- TalkBack 完成：首页选戏、详情、收藏、搜索、返回与错误恢复。
- 分别添加完整链接、带 `p` 的多 P 链接、无 `p` 但含多 P 目录的链接、`b23.tv` 短链接；伪域名、HTTP、非标准端口必须被拒绝。
- 验证断网、证书错误、视频失效、WebView 全屏、返回优先退出全屏及外部打开。
- 验证移动网络首次提醒只出现一次。
- 验证删除剧种时内容迁移到“其他”，删除戏曲时收藏、历史与应用私有封面一并处理。

## 数据与内容边界

所有片单、收藏、历史和封面只保存在本机应用私有目录；按产品约定不提供备份，卸载会清空。家人主动验证完整链接时，应用只读取该公开视频页面内嵌的 BVID、分 P 编号和分 P 标题用于选择，不读取封面或媒体地址，不调用搜索、播放或下载接口。只应收录无需登录或会员的公开视频，并优先使用院团、电视台或明确授权账号的内容。应用不使用 B 站 Logo，也不代表与哔哩哔哩存在官方合作。

## 免责声明

《看戏》为在线播放应用，所有视频均通过 B 站官方外链播放器实时加载播放，播放时会消耗移动数据或 Wi-Fi 流量。首次使用移动网络播放时应用会弹出提醒，后续不再重复提示。请在使用前确认网络环境与流量套餐，避免产生额外费用。应用仅提供视频索引与播放入口，不存储、缓存或下载视频内容，视频版权归原 UP 主及哔哩哔哩所有。

实现依据：[B 站官方外链播放器](https://player.bilibili.com/)、[Android WebView 安全建议](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading)和[Jetpack Compose 官方设置](https://developer.android.com/develop/ui/compose/setup)。

Debug APK 使用 Android 调试签名，仅适合家庭测试。长期分发前应创建由项目所有者保存、且绝不提交 Git 的 Release 签名密钥。
