# 转发平台 (ForwardPlantform)

可视化的 MQTT / Kafka 数据转发处理平台。把原先 6 个独立转发服务
(bridge_sd/sjq/ssd/zdqj/sywyhq、tunnel_bdl) 的能力平台化：在页面上配置
数据源、编写并调试转发处理代码 (Groovy)、配置输出目标，并进行协议生命周期
管理、状态监控、日志查询与按天自动清理。

## 技术栈

- 后端: Java 21 + Spring Boot 3.2、Groovy 4 脚本引擎、LMAX Disruptor、
  Eclipse Paho (MQTT)、spring-kafka、Spring Data JPA、Flyway、Micrometer
- 前端: Vue3 + Vite + TypeScript + Element Plus + Monaco Editor
- 数据库: PostgreSQL（配置表 + 按天 RANGE 分区的日志/明细表）

## 架构

```
数据源(MQTT/Kafka) → SourceConnector → Disruptor 环形缓冲(背压)
   → Worker 线程池 → Groovy 脚本(沙箱) → SinkRouter → 输出目标(MQTT/Kafka/HTTP)
                                        ↘ 异步批量日志 → PostgreSQL 分区表
```

每个「转发协议」= 一个数据源 + 一段处理脚本 + 一个输出目标，独立的
Disruptor 环形缓冲与 Worker 池，缓冲满时对上游形成背压。

## 平台提供给脚本的变量/函数

| 名称 | 说明 |
|---|---|
| `msg` / `payload` | 收到的原始报文(字符串) |
| `ctx` | 元数据: `topic` / `source` / `receivedAt` / `partition` / `offset` |
| `json` | `json.parse(str)` / `json.stringify(obj)` |
| `state` | 跨消息聚合(带 TTL): `state.put/get/remove` |
| `time` | `time.toEpochMillis(x)` / `time.nowMs()` / `time.format(ms, pattern)` |
| `log` | `log.info/warn/error/debug(msg)` |
| `output(data)` | 发送到协议配置的输出目标(平台自动序列化) |
| `output('key', data)` | 发送到指定目标(多目标场景) |

## 快速开始

### 1. 准备 PostgreSQL

```bash
docker run -d --name forward-pg -p 5432:5432 \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=forward_platform postgres:16
```

（可选）本地起一个 MQTT broker 便于联调：

```bash
docker run -d --name emqx -p 1883:1883 -p 18083:18083 emqx/emqx:5
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
# 或打包运行
mvn -DskipTests package
java -jar target/forward-platform.jar
```

数据库连接可用环境变量覆盖：`DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD`。
Flyway 会自动建表；应用启动时会预建当天及未来数天的日志分区。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# 打开 http://localhost:5173 （已代理 /api 到 8080）
```

### 4. 使用流程

1. 「数据源」新建一个 MQTT/Kafka 订阅连接
2. 「输出目标」新建一个 MQTT/Kafka/HTTP 目标
3. 「转发协议」新建协议，选择数据源与输出目标，设置 Worker 数/缓冲大小/日志保留天数/采样率
4. 进入「代码/调试」编写 Groovy 处理脚本，点击「抓取实时样本」或手输样本，「运行调试」查看 `output`/日志，确认无误后「保存为新版本」
5. 回到协议列表点击「启动」，在「监控大盘」查看吞吐/缓冲水位/错误，在「日志查询」查看运行日志与转发明细

## 高并发要点

- Groovy 脚本一次编译、跨线程复用；聚合状态走 `state`，脚本本身无状态
- 每协议独立 Disruptor 环形缓冲 + Worker 池，缓冲满触发背压(Kafka 减速 / MQTT 回调阻塞)
- 输出侧 Kafka 异步批量、MQTT/HTTP 连接复用
- 日志异步批量写入，明细按采样率落库，队列满即丢弃并计数，绝不阻塞主链路
- 日志/明细按天分区，过期分区直接 `DROP`（远快于 DELETE），保留天数取各协议最大值兜底

## 关键配置 (application.yml)

```yaml
forward:
  log:        { queue-capacity: 100000, batch-size: 500, flush-interval-ms: 1000 }
  partition:  { pre-create-days: 3, min-retention-days: 1 }
  script:     { exec-timeout-ms: 3000, compiled-cache-size: 256 }
  runtime:    { default-ring-buffer-size: 16384, default-worker-threads: 4, debug-capture-max: 20 }
```

## 监控

Prometheus 指标: `GET /actuator/prometheus`，每协议指标带 `protocolId` 标签
(`forward.protocol.in/out/script_error/timeout/sink_error/buffer_remaining/avg_cost_ms`)。
