# Oasis AI — Background Removal Service

Self-hosted API used by the Oasis AI Android app. **We own this** — no third-party remove.bg subscription.

## What it does

- Accepts a product photo (JPEG/PNG)
- Removes the background using [rembg](https://github.com/danielgatis/rembg) (U²-Net)
- Returns a transparent PNG

## Install (Windows / mall PC)

```powershell
cd services\background-removal
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

Server listens on **http://0.0.0.0:8765**

First run downloads the ML model (~170 MB) — one-time.

## Android app connection

| Device | Server URL in app Settings |
|--------|---------------------------|
| Emulator | `http://10.0.2.2:8765` (default) |
| Real phone (same Wi‑Fi as PC) | `http://YOUR_PC_IP:8765` |

In the app: **Home → Background Removal Server** → set URL → **Test connection**

## API

### `GET /health`

```json
{ "status": "ok", "service": "oasis-background-removal", "version": "1.0.0" }
```

### `POST /v1/remove-background`

- Body: `multipart/form-data`, field name `file`
- Response: `image/png` (transparent background)

## Production notes

- Run on a mall LAN PC or small server
- For HTTPS, put nginx in front later
- Keep PC awake during photo sessions
