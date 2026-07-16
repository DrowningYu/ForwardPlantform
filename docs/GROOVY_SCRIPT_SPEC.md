# ForwardPlantform Groovy 转发脚本编写规范

> **文档用途**：供 AI 编写转发处理脚本时直接遵循。阅读本文后，应能独立产出可编译、可上线、行为符合业务预期的 Groovy 脚本，无需再翻阅源码。
>
> **平台**：ForwardPlantform（Java 21 + Spring Boot 3 + Groovy 脚本引擎）  
> **脚本语言**：Groovy（编译为 `ForwardScript` 子类，在沙箱内执行）

---

## 目录

1. [平台架构与消息流水线](#1-平台架构与消息流水线)
2. [脚本执行模型（AI 必须理解）](#2-脚本执行模型ai-必须理解)
3. [脚本标准结构](#3-脚本标准结构)
4. [平台注入变量与 API 完整参考](#4-平台注入变量与-api-完整参考)
5. [ctx 元数据字段](#5-ctx-元数据字段)
6. [output 输出规则](#6-output-输出规则)
7. [沙箱与安全限制](#7-沙箱与安全限制)
8. [并发、state 与 workerThreads](#8-并发state-与-workerthreads)
9. [时间戳处理决策树](#9-时间戳处理决策树)
10. [常见业务模式与完整示例](#10-常见业务模式与完整示例)
11. [反模式与已知陷阱](#11-反模式与已知陷阱)
12. [脚本交付检查清单](#12-脚本交付检查清单)

---

## 1. 平台架构与消息流水线

### 1.1 概念模型

| 概念 | 说明 |
|------|------|
| **数据源 (data_source)** | 可复用的 MQTT/Kafka/HTTP(推送接收) 连接配置 |
| **输出目标 (output_target)** | 可复用的 MQTT/Kafka/HTTP 输出配置 |
| **协议 (protocol)** | 一条完整的转发链路：1 个数据源(+可选 Topic 过滤) + 1 个输出目标 + 1 份 Groovy 脚本 |
| **脚本版本 (script_version)** | 协议脚本的版本历史，支持回滚 |

**连接层平台级共享**：每个数据源/输出目标在运行时只维护**一条真实连接**，由平台统一收发。
多个协议绑定同一数据源时，数据按各协议配置的「订阅 Topic」过滤后**复制分发**（fan-out），
不存在连接互斥；多个协议绑定同一输出目标时复用同一发送连接。

每个**协议**仍独占：Disruptor 队列、Worker 线程池、`StateStore`、编译后的脚本 Class——
脚本执行相互隔离，一个协议的异常/超时不影响其他协议。

协议可配置 `sourceTopics`（`|` 分隔，MQTT 支持 `+`/`#` 通配）：
只接收数据源中匹配这些 topic 的消息；**留空 = 接收该数据源全部数据**。

### 1.2 单条消息处理流水线

```
数据源(MQTT/Kafka/HTTP)  ← 平台级共享连接，1 数据源 = 1 连接
    → SourceConnector 收到原始字节
    → 构造 SourceMessage(payload=字符串, ctx=元数据Map)
    → SharedSourceManager 按各协议 Topic 过滤复制分发
    → 写入该协议 Disruptor RingBuffer（满则阻塞 → 背压）
    → Worker 线程取出消息
    → 构建 Binding(msg, ctx, json, time, state, log, output)
    → 执行 Groovy 脚本（默认超时 3000ms）
    → 脚本调用 output(data) → 序列化为 JSON/字符串 → 共享输出连接发送
    → 可选：按 sample_rate 写入转发明细日志
```

注意：同一数据源下若某协议队列长期打满，会阻塞该源的分发线程，
拖慢同源其他协议（不丢数据）。慢协议应调大 `ring_buffer_size` 或优化脚本。

### 1.3 脚本在链路中的职责

脚本只做**业务变换**，不做连接管理：

- **输入**：`msg`（原始字符串）+ `ctx`（topic、时间等元数据）
- **处理**：解析、过滤、字段映射、聚合、校验
- **输出**：调用 `output(...)` 零次或多次

---

## 2. 脚本执行模型（AI 必须理解）

| 维度 | 行为 |
|------|------|
| 编译时机 | 协议启动时编译；保存新版本后若协议正在运行则**重启协议**并重新编译 |
| 热更新 | 等价于 stop → 重新 compile → start；**StateStore 清空** |
| 单条超时 | 默认 **3000ms**（`forward.script.exec-timeout-ms`），超时中断线程 |
| Binding | **每条消息独立**，互不影响 |
| CompiledScript | 协议内所有 Worker **共享**同一份编译结果 |
| StateStore | 协议内所有 Worker **共享**；脚本版本切换/协议重启后**丢失** |
| output 失败 | **不抛异常回脚本**，仅计数；脚本视为已执行完毕 |
| 多条 output | 允许；一次 `onMessage` 可产生 0~N 条输出 |

---

## 3. 脚本标准结构

AI 生成的脚本**必须**按以下结构组织：

```groovy
// ============================================================
// [业务名称] 简要说明
// 源: [topic/描述]  →  目标: [topic/描述]
// 聚合: 无 | state（键规则说明）| 单条内多条 output
// 建议 workerThreads: 1 | 4（说明原因）
// ============================================================

// --- 1. 常量 / 映射表（若有）---
// 放在脚本顶部或方法内；见 §11 顶层变量陷阱

// --- 2. 辅助方法（推荐）---
// 解析、映射、校验逻辑写成方法，避免主流程臃肿

// --- 3. 主流程 ---
def root = json.parse(msg)
if (!(root instanceof Map)) {
    log.warn("消息根节点不是 JSON 对象 | topic=${ctx.topic}")
    return
}

// 校验 → 变换 → output(...)
```

### 3.1 最小可运行模板

```groovy
def obj = json.parse(msg)
output([
    receivedAt: ctx.receivedAt,
    payload   : obj
])
```

### 3.2 带早退与日志的模板

```groovy
def root = json.parse(msg)
if (root == null) {
    log.warn("JSON 解析结果为空 | topic=${ctx.topic}")
    return
}

// 业务处理...
Map out = [deviceId: 'xxx', value: 1.0]
output(out)
```

---

## 4. 平台注入变量与 API 完整参考

脚本中**无需 import** 即可使用下列变量（由平台注入 Binding）。

### 4.1 总览

| 变量 | Java 类型 | 说明 |
|------|-----------|------|
| `msg` | `String` | 原始消息体（UTF-8 字符串） |
| `payload` | `String` | `msg` 的别名 |
| `ctx` | `Map<String, Object>` | 消息元数据 |
| `json` | `JsonHelper` | JSON 解析/序列化 |
| `time` | `TimeHelper` | 时间戳转换 |
| `state` | `StateStore` | 跨消息键值存储 |
| `log` | `ScriptLog` | 写运行日志（入库） |
| `output(...)` | 方法（继承自 `ForwardScript`） | 发送数据到输出目标 |

> **禁止**直接访问 `__sink`（内部变量）。

---

### 4.2 `msg` / `payload`

- 类型：**始终为 String**
- MQTT：消息 payload 原文
- Kafka：record value 转 UTF-8 字符串（二进制非文本时需在上游保证编码）
- 用法：`json.parse(msg)` 或 `json.parse(payload)`

---

### 4.3 `json` — JsonHelper

#### `Object parse(String text)`

将 JSON 字符串解析为 Groovy 对象。

| 输入 | 返回 |
|------|------|
| `null` 或空白 | `null` |
| JSON 对象 | `Map`（LinkedHashMap） |
| JSON 数组 | `List` |
| JSON 基本类型 | 对应 Java 类型 |
| 非法 JSON | **抛异常** `RuntimeException: JSON 解析失败: ...` |

```groovy
def root = json.parse(msg)
if (!(root instanceof Map)) return

def list = json.parse('[1,2,3]')   // List

// Kafka 双层 JSON：外层 payload 字段是字符串
def outer = json.parse(msg)
def inner = json.parse(outer.payload.toString())
```

#### `String stringify(Object obj)`

对象序列化为 JSON 字符串。失败抛 `RuntimeException: JSON 序列化失败: ...`

```groovy
def text = json.stringify([a: 1, b: 2])
```

---

### 4.4 `time` — TimeHelper

#### `long nowMs()`

当前毫秒时间戳。

```groovy
long ts = time.nowMs()
```

#### `Long toEpochMillis(Object value)`

尽力转换为毫秒时间戳；无法解析返回 `null`。

| 输入类型 | 规则 |
|----------|------|
| `null` | `null` |
| `Number` | `longValue()` 直接返回 |
| 纯数字字符串 | 长度 ≤10：视为**秒** ×1000；长度 **12**：×10；长度 **13**：视为毫秒原样；其他长度：原样 parse |
| 日期字符串 | 按**系统默认时区**解析，支持格式：`yyyy-MM-dd HH:mm:ss.SSS`、`yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd'T'HH:mm:ss` |

```groovy
time.toEpochMillis(1700000000)        // 10位秒 → 毫秒
time.toEpochMillis('1700000000000')   // 13位毫秒
time.toEpochMillis('2024-01-15 10:30:00')
time.toEpochMillis('20240115103000')  // 12位特殊：×10（八达岭 tunnel 场景）
```

> **注意**：不同业务对 12 位时间戳处理不同。简易监测（zdqj）的 `collect_time` 用**纯数字 ≤10 位 ×1000、其余按毫秒**，**不做 ×10**。编写前必须对齐原 Java 服务逻辑。

#### `String format(long epochMillis, String pattern)`

毫秒时间戳按**系统默认时区**格式化。

```groovy
def s = time.format(1700000000000L, 'yyyy-MM-dd HH:mm:ss')
```

---

### 4.5 `state` — StateStore

协议级并发 Map，用于跨消息聚合（如三轴 INC/GNSS/VIB 合并、按设备缓存最新值）。

#### API

| 方法 | 签名 | 说明 |
|------|------|------|
| 读取 | `Object get(String key)` | 不存在或已过期返回 `null` |
| 写入 | `void put(String key, Object value)` | 永不过期 |
| 写入+TTL | `void put(String key, Object value, long ttlMs)` | `ttlMs > 0` 时过期 |
| 判断 | `boolean containsKey(String key)` | |
| 删除 | `Object remove(String key)` | 返回旧值 |
| 清空 | `void clear()` | 协议重启时自动丢失 |
| 快照 | `Map<String, Object> snapshotValues()` | 当前未过期所有键值 |

#### 值类型建议

- 存 **`Map`**（`[field: value, ...]`）用于聚合后 `output`
- 键命名建议：`"${类型}:${设备ID}"`，如 `"INC:RTU001"`、`"GNSS:${rtuCode}"`

#### 典型聚合模式

```groovy
void mergeAndOutput(String key, String field, double value, long collectTime) {
    def obj = state.get(key)
    if (obj == null) {
        obj = [rtu_code: key, collect_time: collectTime, x: 0.0, y: 0.0, z: 0.0]
        state.put(key, obj)
    }
    obj[field] = value
    obj.collect_time = collectTime
    output(obj)   // 每次更新都输出（与原服务一致时）
}
```

> **并发警告**：`get` → 修改 Map → 隐式写回，在 `workerThreads > 1` 时有竞态。需要 state 聚合时**强烈建议 `workerThreads = 1`**。

---

### 4.6 `log` — ScriptLog

| 方法 | 级别 | 说明 |
|------|------|------|
| `log.info(Object msg)` | INFO | 写入协议运行日志表 |
| `log.warn(Object msg)` | WARN | 跳过、缺字段等可恢复问题 |
| `log.error(Object msg)` | ERROR | 严重错误 |
| `log.debug(Object msg)` | DEBUG | 调试信息 |

```groovy
log.warn("缺少 device_id | topic=${ctx.topic}")
log.info("处理完成 device=${deviceId}")
```

- 支持 GString：`"deviceId=${id}"`
- 不要在高频路径打大量 `info`（影响性能与日志量）

---

### 4.7 `output` — 发送数据

继承自 `ForwardScript`，脚本内直接调用。

#### `void output(Object data)`

发送到协议配置的**默认输出目标**。

#### `void output(String targetKey, Object data)`

发送到指定目标键（多目标预留；当前每协议通常仅 1 个输出）。

#### 序列化规则（SinkRouter）

| data 类型 | 发送内容 |
|-----------|----------|
| `null` | 空字符串 `""` |
| `String` | 原样发送 |
| `byte[]` | UTF-8 解码为字符串 |
| `Map` / `List` / 其他对象 | Jackson 序列化为 **JSON 字符串** |

```groovy
// 推荐：输出 Map，平台自动 JSON 化
output([deviceId: 'D001', flag: 'TEMP', value: [1.2, 3.4]])

// 输出已是 JSON 字符串时
output('{"deviceId":"D001"}')

// 一条消息多次输出
for (def item : items) {
    output(transform(item))
}
```

- 输出目标为 MQTT 时，payload 即上述序列化结果，topic 由输出目标配置决定
- **发送失败不会抛回脚本**，脚本无法感知单次 send 成败

---

## 5. ctx 元数据字段

`ctx` 是普通 `Map<String, Object>`，**不是**固定类型。按数据源不同字段不同。

### 5.1 MQTT 源

| 键 | 类型 | 说明 |
|----|------|------|
| `topic` | String | 订阅 topic |
| `source` | String | 固定 `"mqtt"` |
| `receivedAt` | Long | 平台收到消息的毫秒时间戳 |
| `qos` | Integer | MQTT QoS |

```groovy
if (ctx.topic?.contains('meteorology')) return   // 按 topic 过滤
log.warn("qos=${ctx.qos} topic=${ctx.topic}")
```

### 5.2 Kafka 源

| 键 | 类型 | 说明 |
|----|------|------|
| `topic` | String | Kafka topic |
| `source` | String | 固定 `"kafka"` |
| `partition` | Integer | 分区号 |
| `offset` | Long | 偏移量 |
| `key` | String | record key（可能为 null） |
| `receivedAt` | Long | 平台收到消息的毫秒时间戳 |

```groovy
log.debug("kafka ${ctx.topic} p${ctx.partition} o${ctx.offset}")
```

### 5.3 HTTP 源（推送接收）

上游向平台 `/ingest/{接口名称}` 推送数据（数据源配置 `path` + `method`）。
POST 时 `msg` = 请求体原文；GET 时 `msg` = query 参数序列化成的 JSON 字符串。

| 键 | 类型 | 说明 |
|----|------|------|
| `topic` | String | 接口名称（path），如 `bridge-abc` |
| `source` | String | 固定 `"http"` |
| `method` | String | `GET` / `POST` |
| `receivedAt` | Long | 平台收到请求的毫秒时间戳 |

```groovy
// GET /ingest/xxx?device=D01&value=3.14 时：
def obj = json.parse(msg)   // [device: 'D01', value: '3.14']（query 值均为字符串）
```

### 5.4 访问方式

```groovy
ctx.topic
ctx['receivedAt']
ctx.get('partition')
```

---

## 6. output 输出规则

1. **可以不调 `output`**：表示丢弃消息（过滤）
2. **可以调多次 `output`**：一条输入 → 多条输出（如山羊洼 data[] 平铺、三家店桥多 device）
3. **输出 Map 的 key**：推荐 **camelCase** 或与下游约定一致（如 `deviceId`、`device_id` 按原系统对齐）
4. **不要 output 超大对象**：影响 MQTT 带宽与序列化耗时
5. **脚本末尾无需 return 值**：返回值被忽略

---

## 7. 沙箱与安全限制

### 7.1 允许

- Groovy 语法、方法定义、闭包
- `java.util.*`、`java.util.stream.*`、`java.time.*`（已 star import）
- 全限定类名：`java.time.LocalDateTime`、`java.time.ZoneId`、`java.time.format.DateTimeFormatter`
- 平台 API：`msg`、`ctx`、`json`、`time`、`state`、`log`、`output`
- 正则：`text ==~ /pattern/`
- Elvis `?:`、安全导航 `?.`、GString

### 7.2 禁止（编译期拒绝）

| 类别 | 禁止内容 |
|------|----------|
| star import | `java.io.*`、`java.nio.*`、`java.net.*`、`java.lang.reflect.*` 等 |
| import | `java.lang.System`、`Runtime`、`Thread`、`ProcessBuilder`、`Process`、`Class` |
| 方法调用 | 对上述类的接收者调用（如 `System.exit()`） |

### 7.3 编译失败示例

```groovy
System.exit(0)              // 编译失败
new java.io.File('/etc')    // 编译失败
```

---

## 8. 并发、state 与 workerThreads

| 场景 | 建议 workerThreads |
|------|-------------------|
| 无 state，一条消息一次 output | 4（默认） |
| 无 state，单条消息内多次 output | 4 |
| **使用 state 跨消息聚合** | **1**（必须） |
| 依赖消息顺序合并 | **1** |

协议配置字段：

- `ring_buffer_size`：默认 16384，队列满时反压数据源
- `worker_threads`：Worker 数量
- `sample_rate`：转发明细采样率 0~1

---

## 9. 时间戳处理决策树

编写脚本前，先确认原系统的 `collect_time` / `timestamp` / `sample_time` 规则：

```
输入时间字段
├─ 数字或数字字符串
│   ├─ 长度 ≤ 10        → 秒，× 1000 得毫秒（简易监测 collect_time）
│   ├─ 长度 12          → 视业务而定：
│   │                      • tunnel header.timestamp：× 10（用 time.toEpochMillis）
│   │                      • 简易监测：按毫秒，不 ×10
│   └─ 长度 13          → 已是毫秒
├─ "yyyy-MM-dd HH:mm:ss[.SSS]"  → time.toEpochMillis 或 Asia/Shanghai 手动解析
└─ 测点内嵌 time 字段（山羊洼）→ 取 Number 或 Long.parseLong
```

**优先使用 `time.toEpochMillis`**，仅在业务明确不一致时写自定义 `parseXxxTime()` 方法。

---

## 10. 常见业务模式与完整示例

### 10.1 模式 A：JSON 直通（调试/探针）

```groovy
def obj = json.parse(msg)
output([receivedAt: ctx.receivedAt, payload: obj])
```

### 10.2 模式 B：按 topic 过滤后映射

```groovy
String flag = ctx.topic?.split('/')?.getAt(3)
if (flag == 'meteorology') return   // 订阅但不转发

def root = json.parse(msg)
// ... 按 flag 分支映射 ...
output([device_id: deviceId, flag: flag, timestamp: ts, data1: v1])
```

### 10.3 模式 C：数组平铺 — 一条输入多次 output

```groovy
def root = json.parse(msg)
def dataList = root.data
if (!(dataList instanceof List)) return

String deviceId = root.deviceId ?: 'unknown'
for (def item : dataList) {
    if (!(item instanceof Map)) continue
    Map flat = buildFlatItem(deviceId, root.deviceType as int, item)
    if (flat != null) output(flat)
}
```

### 10.4 模式 D：Kafka 双层 JSON + 按 device 去重

```groovy
def root = json.parse(msg)
def payloadStr = root.payload?.toString()?.trim()
if (!payloadStr) return

def payload = json.parse(payloadStr)
def arr = payload.mqtt_data
if (!(arr instanceof List)) return

// 同一 device_id 只保留 sample_time 最大的一条
def latest = [:] as LinkedHashMap
for (def item : arr) {
    if (!(item instanceof Map)) continue
    def id = item.device_id?.toString()?.trim()
    if (!id) continue
    long st = (item.sample_time instanceof Number) ? item.sample_time.longValue() : 0L
    if (!latest.containsKey(id) || st > latest[id].st) {
        latest[id] = [item: item, st: st]
    }
}
latest.values().each { entry ->
    def out = buildForwardMessage(entry.item as Map)
    if (out) output(out)
}
```

### 10.5 模式 E：state 跨消息三轴聚合（workerThreads=1）

```groovy
void processTriple(String kind, Map fieldByPara, String rtuCode,
                   String paraType, double value, long collectTime) {
    String field = fieldByPara[paraType]
    if (!field) return
    String key = "${kind}:${rtuCode}"
    def obj = state.get(key)
    if (obj == null) {
        obj = [rtu_code: rtuCode, collect_time: collectTime]
        fieldByPara.values().each { f -> obj[f] = 0.0 }
        state.put(key, obj)
    }
    obj[field] = value
    obj.collect_time = collectTime
    output(obj)
}
```

### 10.6 模式 F：设备 ID → flag 映射表

```groovy
Map buildFlagOverride() {
    def m = [:]
    ['SJDQ-HPT-G16-001-01', 'SJDQ-HPT-G16-001-02'].each { m[it] = 'Deflection' }
    return m
}

// 在方法内构建，不要依赖顶层变量传入其他方法（见 §11）
Map buildForwardMessage(Map mqttData, Map flagOverride) {
    def deviceId = mqttData.device_id?.toString()?.trim()
    def flag = flagOverride.getOrDefault(deviceId, mqttData.device_type?.toString() ?: '')
    // ...
}
```

---

## 11. 反模式与已知陷阱

### 11.1 顶层变量在方法内不可见（Groovy 作用域）

**错误**：顶层 `def MAP = [...]`，在下方**方法定义内**直接引用 `MAP` → 运行时报 `MissingPropertyException`。

**正确**：

```groovy
// 方案 1：映射表在方法内构建
Map buildMap() { [...] }

// 方案 2：映射表作为参数传入
Map transform(Map data, Map flagOverride) { ... }

// 方案 3：解析逻辑写在方法内部，不依赖顶层变量
Long parseCollectTime(Object raw) {
    // 完整逻辑写在这里
}
```

### 11.2 在循环中重复 json.parse 同一字符串

一次 parse 即可，避免性能浪费。

### 11.3 缺字段时 silent return

应 `log.warn(...)` 说明原因，便于线上排查。

### 11.4 state 聚合 + 多 Worker

必然竞态，**必须 workerThreads=1**。

### 11.5 脚本执行超时（默认 3s）

避免大循环、复杂正则、超大 JSON。长逻辑拆方法但仍需注意总耗时。

### 11.6 JSON 字段类型不确定

Kafka/物联网数据常出现数字有时是 String。用 `instanceof Number` 分支处理：

```groovy
long v = (raw instanceof Number) ? raw.longValue() : Long.parseLong(raw.toString())
```

### 11.7 单引号与 Groovy 字符串

平台存储脚本时字符串内的单引号可能写成 `''`（Groovy 转义）。AI 生成时使用正常 `'` 即可，保存后平台处理。

---

## 12. 脚本交付检查清单

AI 完成脚本后，逐项自检：

- [ ] 文件头注释：业务名、源、目标、是否 state、建议 workerThreads
- [ ] 使用 `json.parse(msg)` 解析，并校验根节点类型
- [ ] 缺字段 / 非法格式有 `log.warn` 且 `return`
- [ ] 时间戳规则与原 Java 服务一致（见 §9）
- [ ] 使用 state 时注释标明 `workerThreads=1`
- [ ] 顶层变量未在方法内直接引用（见 §11.1）
- [ ] 未使用禁止 API（System、IO、反射等）
- [ ] `output` 的 Map 字段名与下游 MQTT 消费者约定一致
- [ ] 过滤型业务（如不转发 meteorology）有明确 `return`
- [ ] 单条消息多设备/多测点场景，确认 output 次数符合预期
- [ ] 无无限循环；循环内逻辑轻量

---

## 附录 A：协议配置与脚本关系

```
协议
├── source_id        → data_source（MQTT/Kafka/HTTP 连接，平台级共享）
├── source_topics    → 订阅 Topic 过滤（| 分隔，MQTT 支持 +/# 通配；空 = 全部）
├── output_target_id → output_target（MQTT/Kafka/HTTP + topic/url，平台级共享）
├── current_version_id → script_version.code（当前执行的 Groovy）
├── worker_threads   → 脚本并发度（见 §8）
├── ring_buffer_size → 队列长度
├── enabled          → 是否随平台启动自启
└── sample_rate      → 转发明细采样率
```

脚本**不配置**连接信息；连接全在数据源/输出目标中。脚本只负责 `msg` → `output(data)`。

---

## 附录 B：参考脚本路径

| 业务 | 路径 | 要点 |
|------|------|------|
| 简易监测（state 聚合） | `bridge/script/简易监测.java` | state、collect_time 解析、workerThreads=1 |
| 三家店桥（多 device output） | `bridge/script/三家店桥.java` | Kafka 双层 JSON、device 去重、flag 映射 |
| 平台初始模板 | `ProtocolService.STARTER_CODE` | 最小示例 |

---

## 附录 C：AI 生成脚本时的输出格式要求

当用户要求编写转发脚本时，AI 应：

1. **先说明**：数据源类型（MQTT/Kafka）、是否需 state、建议 workerThreads
2. **输出完整 Groovy 代码**，含文件头注释
3. **不输出** Java 类、不 import 禁止包
4. 若迁移自现有 Java 服务，**显式列出**与原服务对齐的字段映射表
5. 代码可直接粘贴到平台「脚本编辑器」保存

---

*文档版本：与 ForwardPlantform backend 同步（Groovy 沙箱、TimeHelper、StateStore、SinkRouter）*
