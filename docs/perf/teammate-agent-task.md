# 队友性能分支任务卡

分支名：

```text
feature/perf-telemetry-upload-fastpath
```

## 目标

只优化和量化这段链路：

```text
Android bitmap 压缩/降采样 -> multipart 请求发送 -> FastAPI 接收 -> API 返回
```

2 秒目标的口径是：用户按按钮到手机开始播报。你的分支不需要单独完成 2 秒端到端目标，但要让主线能清楚看到慢在压缩、上传、后端接收、语义模型还是 DAP 推理。

## 边界

允许修改：

- Android 上传链路中的图片压缩、降采样、JPEG 质量选择。
- Android 端日志和轻量性能数据结构。
- 云端 `/analyze`、`/semantic-analyze` 的耗时日志或可选 debug 字段，不能破坏现有响应字段。
- 单测、README、性能测试说明文档。

禁止修改：

- 不改 `CameraManager` 的 X4 OSC 拍照、轮询、下载逻辑。
- 不改主界面布局、一级按钮、导航结构。
- 不改 DAP runner、DAP 权重、外部 DAP 仓库。
- 不提交 `.env`、模型权重、影石 SDK、`.aar/.so/.rar/.pth/.onnx/.safetensors`、样张大文件。
- 不把 fallback、路演脚本、固定文案当成真实性能结果。

## 具体交付

1. 新增可配置上传规格。

```text
FAST_ANALYZE:
- 长边 960 或 1280
- JPEG 质量 70-80
- 用于 /analyze 和 /semantic-analyze

HIGH_QUALITY:
- 保留现有策略
- 用于对比和排查压缩造成的信息损失
```

2. 增加 Android 端耗时埋点。

- bitmap 降采样耗时。
- JPEG 文件大小。
- multipart 请求耗时。
- 后端返回的 `latency_ms`。
- Android 端总耗时。

3. 增加后端日志。

- 收到图片字节数。
- 图片宽高。
- `/analyze` 接收处理耗时。
- `/semantic-analyze` 接收处理耗时。

4. 增加文档：

```text
docs/perf/latency-baseline.md
```

文档需要说明：

- 如何在没有 X4、没有 GPU、没有 DAP 的环境下用开发样张跑上传性能测试。
- 如何记录压缩前尺寸、压缩后尺寸、JPEG 文件大小、请求耗时、后端 `latency_ms`。
- 哪些数据只代表上传链路，不代表真实 X4 端到端耗时。

5. 增加测试。

- Android 单测覆盖压缩规格选择。
- 后端测试确认新增日志或 debug 行为不破坏现有 API 字段。
- 确认 `/analyze` 仍返回 `distance_m/level/confidence/scene_text/latency_ms`。
- 确认 `/semantic-analyze` 仍返回 `mode/summary/objects/traffic_light/target_found/product_name/confidence/latency_ms/fallback_reason`。

## 验收标准

- 无 X4、无 GPU 的环境下也能跑通开发样张上传性能测试。
- PR diff 不包含 UI 大改、`CameraManager` 改动、DAP runner 改动。
- README 或 perf 文档能让主线开发者按步骤复现上传耗时。
- Android 单测和云端测试通过。

推荐验证命令：

```powershell
cd D:\MyProject\Bohack2\OmniEye-Mobile-roadshow
$env:JAVA_HOME='D:\MyProject\Bohack2\.tooling\jdk17\jdk-17.0.19+10'
$env:ANDROID_HOME='D:\MyProject\Bohack2\.tooling\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
& 'C:\Users\EZ\.gradle\wrapper\dists\gradle-8.11.1-bin\7800bkpvjdl6wgx6vnys98319\gradle-8.11.1\bin\gradle.bat' testDebugUnitTest assembleDebug --no-daemon

& 'D:\MyProject\Bohack2\.tooling\python312\python.exe' -m pytest cloud-backend -q --basetemp D:\MyProject\Bohack2\.pytest_tmp_perf -p no:cacheprovider
```

提交前安全扫描：

```powershell
git ls-files | rg -n "(?i)(\.aar$|\.so$|\.rar$|\.zip$|\.pth$|\.onnx$|\.safetensors$|weights|model\.pth|赛事SDK|DAP-weights|\.env$)"
```
