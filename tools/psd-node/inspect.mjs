#!/usr/bin/env node
/**
 * Secondary PSD inspector using ag-psd (cross-check vs psd-tools).
 *
 * Usage:
 *   node tools/psd-node/inspect.mjs path/to/file.psd
 *   node tools/psd-node/inspect.mjs --all
 */

import { readFileSync, writeFileSync, mkdirSync, readdirSync, statSync } from "node:fs";
import { join, basename, resolve, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { readPsd } from "ag-psd";

const ROOT = resolve(fileURLToPath(new URL("../..", import.meta.url)));
const INBOX = join(ROOT, "templates", "psd-inbox");
const SPECS = join(ROOT, "templates", "psd-specs");

const ROLE_PATTERNS = [
  ["product_image", /product|photo|image|pack|packshot|png|visuel/i],
  ["designation", /designat|name|title|libell|product.?name|nom/i],
  ["price", /^price$|prix|tarif|promo.?price|new.?price|prix.?promo/i],
  ["original_price", /old.?price|prix.?bar|barr|was|ancien|before|orig/i],
  ["barcode", /barcode|ean|code.?barre|cb/i],
  ["background", /background|bg|fond|yellow|jaune/i],
  ["logo", /logo|brand|marque/i],
];

function inferRole(name, text) {
  const hay = `${name} ${text || ""}`;
  for (const [role, re] of ROLE_PATTERNS) {
    if (re.test(hay)) return role;
  }
  return null;
}

function walkLayers(layers, path = "", depth = 0, out = [], index = { n: 0 }) {
  for (const layer of layers || []) {
    const name = layer.name || `layer_${index.n}`;
    const fullPath = path ? `${path}/${name}` : name;
    const left = layer.left ?? 0;
    const top = layer.top ?? 0;
    const right = layer.right ?? left;
    const bottom = layer.bottom ?? top;
    const textValue = layer.text?.text ?? layer.text?.value ?? null;
    out.push({
      index: index.n++,
      name,
      path: fullPath,
      depth,
      kind: layer.text ? "type" : layer.children ? "group" : "pixel",
      visible: layer.hidden !== true,
      opacity: layer.opacity != null ? layer.opacity : 1,
      blendMode: layer.blendMode || "normal",
      bounds:
        right > left && bottom > top
          ? { left, top, right, bottom, width: right - left, height: bottom - top }
          : null,
      text: textValue
        ? {
            value: String(textValue).trim(),
            font_name: layer.text?.style?.font?.name ?? null,
            font_size: layer.text?.style?.fontSize ?? null,
            color: null,
          }
        : null,
      role_hint: inferRole(name, textValue),
      children_count: layer.children?.length ?? 0,
    });
    if (layer.children?.length) {
      walkLayers(layer.children, fullPath, depth + 1, out, index);
    }
  }
  return out;
}

function inspectFile(psdPath) {
  const buffer = readFileSync(psdPath);
  const psd = readPsd(buffer, { skipLayerImageData: true });
  const layers = walkLayers(psd.children);
  const roleSummary = {};
  for (const layer of layers) {
    if (!layer.role_hint) continue;
    (roleSummary[layer.role_hint] ||= []).push(layer.path);
  }
  const spec = {
    source_file: basename(psdPath),
    engine: "ag-psd",
    document: {
      width: psd.width,
      height: psd.height,
      channels: psd.channels,
      color_mode: psd.colorMode,
      dpi_x: psd.imageResources?.resolutionInfo?.horizontalResolution ?? null,
      dpi_y: psd.imageResources?.resolutionInfo?.verticalResolution ?? null,
    },
    layers,
    role_summary: roleSummary,
    warnings: [],
  };
  mkdirSync(SPECS, { recursive: true });
  const outPath = join(SPECS, `${basename(psdPath, ".psd")}.ag-psd-spec.json`);
  writeFileSync(outPath, JSON.stringify(spec, null, 2), "utf8");
  console.log(`  spec → ${relative(ROOT, outPath)}`);
  console.log(
    `  ${spec.document.width}×${spec.document.height}px · ${layers.length} layers · roles: ${Object.keys(roleSummary).join(", ") || "none"}`,
  );
}

function collectPsd(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) out.push(...collectPsd(full));
    else if (name.toLowerCase().endsWith(".psd")) out.push(full);
  }
  return out;
}

const args = process.argv.slice(2);
const all = args.includes("--all");
const fileArg = args.find((a) => !a.startsWith("--"));

let files = [];
if (all) {
  files = collectPsd(INBOX);
  if (!files.length) {
    console.error(`No .psd in ${relative(ROOT, INBOX)}`);
    process.exit(1);
  }
} else if (fileArg) {
  files = [resolve(fileArg)];
} else {
  console.log("Usage: node inspect.mjs <file.psd> | --all");
  process.exit(1);
}

console.log(`ag-psd: inspecting ${files.length} file(s)…`);
for (const f of files) {
  console.log(`\n${basename(f)}`);
  try {
    inspectFile(f);
  } catch (err) {
    console.error(`  ERROR: ${err.message}`);
    process.exit(1);
  }
}
console.log("\nDone.");
