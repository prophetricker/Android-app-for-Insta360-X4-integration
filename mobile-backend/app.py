from time import perf_counter

from fastapi import FastAPI, File, UploadFile
from pydantic import BaseModel


app = FastAPI(title="OmniEye Mobile Backend")


class AnalyzeResponse(BaseModel):
    distance_m: float
    level: int
    confidence: float
    scene_text: str
    latency_ms: int


def classify_distance(distance_m: float) -> int:
    if distance_m < 0.4:
        return 4
    if distance_m < 0.8:
        return 3
    if distance_m < 1.5:
        return 2
    if distance_m < 3.0:
        return 1
    return 0


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(frame: UploadFile = File(...)):
    start = perf_counter()
    await frame.read()
    distance_m = 1.2
    level = classify_distance(distance_m)
    latency_ms = int((perf_counter() - start) * 1000)
    return AnalyzeResponse(
        distance_m=distance_m,
        level=level,
        confidence=0.5,
        scene_text="前方道路可通行，右前方可能有障碍物。",
        latency_ms=latency_ms,
    )
