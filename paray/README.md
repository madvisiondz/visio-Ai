# PARAY — side-living visual agent

PARAY is built for **Oasis AI**, but lives as his **own object** with a dedicated home folder and UI.

## Philosophy

> *I live at home. I work at Oasis.*

| Place | Role |
|-------|------|
| **PARAY home** (`paray_home/` on phone) | Memory, logs, manifest, neural files — persists independently |
| **Oasis AI office** | Design, Scan & shoot, catalog, shelf export — where PARAY goes to work |

## Phone folder layout

```
files/paray_home/
├── manifest.json       # PARAY identity
├── office_link.json    # last Oasis workplace visit
├── memory/             # visual_index, fingerprints, barcode_patterns
├── sessions/           # active scan lock, etc.
└── logs/               # learn_events.jsonl
```

## PC exports

```
exports/paray/paray_fingerprint_index.json   # bulk teach (Option A script)
```

## Repo

| Path | Content |
|------|---------|
| `android/.../domain/paray/` | Agent code |
| `android/.../ui/screens/parayhome/` | PARAY home UI (unique theme) |
| `scripts/build_paray_embeddings.py` | PC fingerprint builder |

See `docs/PARAY.md` for full spec.
