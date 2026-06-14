"""
Oasis AI — self-hosted background removal API.

Run on a PC or server on the mall network. The Android app uploads a product photo
and receives a transparent PNG.

Usage:
    pip install -r requirements.txt
    python main.py
"""

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import Response
from rembg import remove

app = FastAPI(
    title="Oasis AI Background Removal",
    description="Self-hosted background removal for OASIS MALL product images",
    version="1.0.0",
)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "oasis-background-removal",
        "version": "1.0.0",
    }


@app.post("/v1/remove-background")
async def remove_background(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Upload must be an image file")

    try:
        raw = await file.read()
        if len(raw) == 0:
            raise HTTPException(status_code=400, detail="Empty file")
        if len(raw) > 20 * 1024 * 1024:
            raise HTTPException(status_code=400, detail="File too large (max 20 MB)")

        output_png = remove(raw)
        return Response(content=output_png, media_type="image/png")
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8765)
