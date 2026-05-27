# 上传链路延迟基线

本文档用于记录 `feature/perf-telemetry-upload-fastpath` 的性能测试结果。当前阶段只测上传链路，不代表真实 X4 端到端耗时。

## 测试口径

端到端目标口径：

```text
用户按按钮 -> X4 取帧 -> Android 压缩 -> 上传云端 -> 云端分析 -> 返回结果 -> 手机开始 TTS
```

本文件只记录其中一段：

```text
Android bitmap 压缩/降采样 -> multipart 请求发送 -> FastAPI 接收 -> API 返回
```

## 环境

填写测试时的环境：

```text
日期：
分支：
设备：
网络：
后端 URL：
是否连接 X4：
是否启用 DAP：
是否启用 OpenAI：
```

## 记录表

| 场景 | API | 上传规格 | 原始尺寸 | 上传尺寸 | JPEG KB | 压缩 ms | 请求 ms | 后端 latency_ms | Android 总 ms | 备注 |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 开发样张 | `/analyze` | FAST_ANALYZE |  |  |  |  |  |  |  |  |
| 开发样张 | `/semantic-analyze mode=surroundings` | FAST_ANALYZE |  |  |  |  |  |  |  |  |
| 开发样张 | `/analyze` | HIGH_QUALITY |  |  |  |  |  |  |  |  |

## 注意事项

- fallback 响应只能证明 API 合同和上传链路可用，不能代表 DAP 或语义模型真实性能。
- 无 X4 时使用 `DevelopmentSampleFrame`，不能代表 X4 拍照、下载和双网络路由耗时。
- 无 GPU 或未启用 DAP 时，`/analyze` 的 `latency_ms` 不代表真实深度推理耗时。
- 记录性能时不要提交样张大文件、临时上传缓存或模型输出图。
