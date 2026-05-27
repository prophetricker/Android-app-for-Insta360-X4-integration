# Perf Upload Fastpath — 5-Agent 协作计划（保存版）

目标：围绕 `feature/perf-telemetry-upload-fastpath`，只优化并量化上传链路：

```text
Android bitmap 压缩/降采样 -> multipart 请求发送 -> FastAPI 接收 -> API 返回
```

参考任务卡与基线模板：
- `docs/perf/teammate-agent-task.md`
- `docs/perf/latency-baseline.md`

---

## 总体交付（来自任务卡）

1. 新增可配置上传规格：

```text
FAST_ANALYZE: 长边 960 或 1280；JPEG 质量 70-80；用于 /analyze 与 /semantic-analyze
HIGH_QUALITY: 保留现有策略；用于对比与排查信息损失
```

2. Android 端耗时埋点（至少）：
- bitmap 降采样耗时
- JPEG 文件大小
- multipart 请求耗时
- 后端返回 `latency_ms`
- Android 端总耗时

3. 后端日志（至少）：
- 收到图片字节数
- 图片宽高
- `/analyze` 接收处理耗时
- `/semantic-analyze` 接收处理耗时

4. 文档补齐：`docs/perf/latency-baseline.md`（已存在，需按要求补充/填写）

5. 测试：
- Android 单测覆盖压缩规格选择
- 后端测试确认新增日志或 debug 行为不破坏现有 API 字段
- 确认两条 API 的返回字段合同不变（尤其 `latency_ms`）

---

## 5-Agent 分工（并行协作）

> 说明：这里的“Agent”指并行子任务负责人。每个 Agent 输出一个最小可合并的变更集（代码 + 测试 + 必要文档），避免互相阻塞。

### Agent1_AndroidCompressionSpec
**负责**：Android 上传规格与图片预处理策略

**工作内容**
- 定义上传规格枚举/配置（`FAST_ANALYZE` / `HIGH_QUALITY`）
- 实现 bitmap 预处理：长边限制（960/1280 二选一，按任务卡）+ 降采样 + JPEG 质量（70-80）
- 保留现有策略作为 `HIGH_QUALITY`

**输出**
- 新的规格定义与压缩入口（供上传调用）
- 可在无 X4 环境用开发样张运行

**依赖/输入**
- 现有上传代码入口位置（由 Agent3 提供具体调用点/文件路径）

---

### Agent2_AndroidTelemetry
**负责**：Android 端性能埋点与日志结构

**工作内容**
- 设计轻量数据结构（例如 `UploadTelemetry`）记录：
  - 原始尺寸、上传尺寸
  - 压缩/降采样耗时（ms）
  - JPEG 大小（KB）
  - multipart 请求耗时（ms）
  - 后端返回 `latency_ms`
  - Android 总耗时（ms）
- 日志输出（Logcat）+（可选）在 UI 现有区域展示，不改主界面布局/一级按钮/导航结构

**输出**
- 可复制到 `docs/perf/latency-baseline.md` 表格的字段

**依赖/输入**
- Agent1 的压缩函数提供“压缩耗时、输出尺寸、输出字节数”等可观测点
- Agent3 的网络请求封装提供“请求耗时、响应体 latency_ms”

---

### Agent3_BackendReceiveLogging
**负责**：云端 FastAPI `/analyze` 与 `/semantic-analyze` 接收侧日志

**工作内容**
- 在不破坏现有响应字段的前提下，增加日志：
  - 收到的图片字节数
  - 图片宽高
  - endpoint 处理耗时
-（可选）增加 debug 字段，但必须可开关且默认不影响响应合同

**输出**
- 后端日志可用于定位慢在：接收/解码/前处理/推理（后续主线继续）

**依赖/输入**
- 现有后端项目路径与测试入口（repo 内的 `cloud-backend` 结构）

---

### Agent4_TestsAndContracts
**负责**：测试与接口合同保护

**工作内容**
- Android 单测：覆盖上传规格选择逻辑（FAST/HQ）
- 后端测试：确认新增日志/debug 不破坏 API 字段
- 明确断言：
  - `/analyze` 仍返回 `distance_m/level/confidence/scene_text/latency_ms`
  - `/semantic-analyze` 仍返回 `mode/summary/objects/traffic_light/target_found/product_name/confidence/latency_ms/fallback_reason`

**输出**
- 可靠的回归保护，保证主线合并不炸

---

### Agent5_DocsAndBenchmarkProcedure
**负责**：把“怎么测”写清楚，并能在无 X4 / 无 GPU / 无 DAP 环境跑通

**工作内容**
- 完善 `docs/perf/latency-baseline.md`：
  - 环境填写项
  - 用 `DevelopmentSampleFrame`（或项目既有开发样张机制）跑测试的步骤
  - 如何记录：压缩前后尺寸、JPEG KB、压缩 ms、请求 ms、后端 latency_ms、Android 总 ms
  - 明确“哪些数据只代表上传链路，不代表真实 X4 端到端耗时”
- 提供一条可复现的最小命令/步骤（Android + backend 测试）

**输出**
- 主线开发者按步骤可复现上传耗时

---

## 关键约束（必须遵守）

- 不改 `CameraManager` 的 X4 OSC 拍照/轮询/下载逻辑
- 不做 UI 大改（不改主界面布局、一级按钮、导航）
- 不改 DAP runner / 权重 / 外部 DAP 仓库
- 不提交任何敏感或大体积文件：`.env`、模型权重、`.aar/.so/.rar/.pth/.onnx/.safetensors`、样张大文件等

---

## 建议的集成顺序

1. Agent1 完成并提供压缩/降采样 API（可独立单测）
2. Agent3 完成后端日志（可独立 pytest）
3. Agent2 接入网络请求调用点，打通端到端埋点字段
4. Agent4 补齐测试与合同断言
5. Agent5 完善基线文档与复现步骤，并用一轮开发样张填表
