# OmniEye Cloud Backend

FastAPI backend for the Android-first OmniEye flow.

```text
Android frame upload -> cloud-backend /analyze -> latest DAP depth result -> distance/level/scene_text
Android frame upload -> cloud-backend /semantic-analyze -> vision semantics -> product / traffic-light guidance
```

## Run the lightweight service

```powershell
cd D:\MyProject\Bohack2\OmniEye-Mobile
python -m pip install -r cloud-backend\requirements.txt
python -m uvicorn omnieye_cloud.main:app --app-dir cloud-backend --host 0.0.0.0 --port 8000
```

Health check:

```powershell
curl.exe http://127.0.0.1:8000/health
```

Upload a frame:

```powershell
curl.exe -F "frame=@sample.jpg" http://127.0.0.1:8000/analyze
```

Semantic product demo:

```powershell
curl.exe -F "frame=@shelf.jpg" -F "mode=product" -F "query=牛奶" http://127.0.0.1:8000/semantic-analyze
```

Semantic traffic-light demo:

```powershell
curl.exe -F "frame=@crossing.jpg" -F "mode=traffic_light" http://127.0.0.1:8000/semantic-analyze
```

When DAP is not configured yet, `/analyze` still returns an Android-compatible fallback:

```json
{
  "distance_m": 3.5,
  "level": 0,
  "confidence": 0.0,
  "scene_text": "正在分析前方环境",
  "latency_ms": 0
}
```

When `OPENAI_API_KEY` is not configured, `/semantic-analyze` also returns a stable demo fallback so the Android app can keep recording and presenting the product / traffic-light flow. To enable real visual semantic analysis:

```powershell
$env:OPENAI_API_KEY = "sk-..."
$env:OPENAI_VISION_MODEL = "gpt-4.1-mini"
python -m uvicorn omnieye_cloud.main:app --app-dir cloud-backend --host 0.0.0.0 --port 8000
```

The semantic endpoint supports:

```text
mode=product       query=目标商品，例如 牛奶、矿泉水、药盒
mode=traffic_light query 可省略
```

## DAP setup

DAP and model weights stay outside this Git repository.

```powershell
git clone https://github.com/Insta360-Research-Team/DAP D:\Models\DAP
```

Create a separate Python environment for DAP, because the current repo Python may not match DAP's tested stack. On the current Windows dev machine, the working environment lives outside Git at `D:\MyProject\Bohack2\.tooling\dap-venv`.

```powershell
D:\MyProject\Bohack2\.tooling\python312\python.exe -m venv D:\MyProject\Bohack2\.tooling\dap-venv
D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe -m pip install -r D:\Models\DAP\requirements.txt
D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe -m pip install --force-reinstall torch==2.7.1 torchvision==0.22.1 --index-url https://download.pytorch.org/whl/cu128
D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe -m pip install pillow==10.4.0 MarkupSafe==2.1.5
```

Download the pretrained model from:

```text
https://huggingface.co/Insta360-Research/DAP-weights
```

Set environment variables before starting `cloud-backend`:

```powershell
$env:DAP_REPO_DIR = "D:\Models\DAP"
$env:DAP_WEIGHTS_PATH = "D:\Models\DAP-weights-repo\model.pth"
$env:DAP_DEVICE = "cuda"
$env:DAP_PYTHON = "D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe"
$env:DAP_DEPTH_SCALE = "100"
python -m uvicorn omnieye_cloud.main:app --app-dir cloud-backend --host 0.0.0.0 --port 8000
```

The backend invokes DAP as an external runner and reads DAP's generated `depth_npy\000001.npy`. DAP's public inference output is normalized to the 0-1 range used by its 100m training/evaluation setup, so the backend multiplies by `DAP_DEPTH_SCALE` before applying meter thresholds. Keep the default at `100` for the MVP and tune it during real-world calibration.

## Android contract

Android should send:

```text
POST /analyze
multipart form-data field: frame
```

For visual semantic analysis:

```text
POST /semantic-analyze
multipart form-data fields:
  frame: image file
  mode: product | traffic_light
  query: optional target, for product mode
```

Response:

```json
{
  "distance_m": 1.2,
  "level": 2,
  "confidence": 0.8,
  "scene_text": "前方约一米有障碍物，请减速。",
  "latency_ms": 25
}
```

Semantic response:

```json
{
  "mode": "traffic_light",
  "summary": "前方是绿灯，但请确认周围安全后通行。",
  "objects": ["traffic light", "crosswalk"],
  "traffic_light": "green",
  "target_found": true,
  "product_name": null,
  "confidence": 0.82,
  "latency_ms": 1500
}
```

`level` uses the shared thresholds:

```text
< 0.4m -> 4
< 0.8m -> 3
< 1.5m -> 2
< 3.0m -> 1
else   -> 0
```

## Tests

```powershell
python -m pytest cloud-backend -q --basetemp D:\MyProject\Bohack2\.pytest_tmp_repo -p no:cacheprovider
```

`--basetemp` avoids local Windows temp-directory permission issues observed on this machine.

## Git branch

Work should stay on:

```text
cloud-backend-roadshow
```

Push to:

```powershell
git remote add android-insta360 https://github.com/prophetricker/Android-app-for-Insta360-X4-integration.git
git push -u android-insta360 cloud-backend-roadshow
```
