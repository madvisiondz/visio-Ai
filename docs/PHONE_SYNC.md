# Phone sync (hotspot) — v1.4.0

Offline **master/slave** sync when several phones shoot different shelves on the same day.

## Roles

| Role | Phone | Job |
|------|-------|-----|
| **Master** | One receiver (often the phone with the full Gestium CSV import) | Runs HTTP server on port **8776**; shares text catalog; receives new PNGs |
| **Slave** | 2–3 shooters | Pulls master catalog, compares, sends only PNGs master does not have |

## Setup (hotspot)

1. **Master:** enable mobile hotspot.
2. **Slaves:** join that Wi‑Fi.
3. All phones: **Settings → Phone sync (hotspot)** — same **PIN** (default `2468`).
4. **Master:** choose **Master (receiver)** → **Start receiver** → note **IP** (e.g. `192.168.43.1`).
5. **Slave:** choose **Slave (sender)** → enter master IP → **Pull master catalog & compare** → **Send my new PNGs to master**.

## Protocol

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/sync/phone/ping` | GET | Handshake |
| `/sync/phone/catalog` | GET | JSON catalog (barcode, codeart, designation, has_image) + TSV text |
| `/sync/phone/catalog.txt` | GET | Tab-separated catalog only |
| `/sync/phone/push` | POST | Multipart: manifest JSON + PNG files |

Auth: header `X-Oasis-Pin`.

## What gets synced

- Transparent PNGs linked to articles (`product_images` FOUND)
- Alternate barcodes linked on slave
- Master skips barcodes/codearts that already have a PNG

## Module

`com.oasismall.oasisai.domain.phonesync`

No cloud. Offline LAN only — **phone → phone**.
