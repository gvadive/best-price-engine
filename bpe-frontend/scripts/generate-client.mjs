#!/usr/bin/env node
// Regenerates src/generated-client from pricing-engine-service's live OpenAPI
// spec. springdoc auto-detects a `servers` entry from whatever host/port the
// spec was fetched through, which previously baked a stale, environment-
// specific URL (http://localhost:19091) straight into the generated client.
// The client is meant to hit the same relative /api path nginx and the vite
// dev proxy both already route -- so BASE is forced back to '' after codegen.
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";
import path from "node:path";

const specUrl = process.env.PRICING_ENGINE_OPENAPI_URL || "http://localhost:8082/v3/api-docs";
const outDir = path.resolve(fileURLToPath(import.meta.url), "../../src/generated-client");

// openapi-typescript-codegen's own URL fetch (via json-schema-ref-parser) fails
// to resolve a live http:// input on current Node -- fetch the spec ourselves
// and hand codegen a local file instead, which its plain fs reader handles fine.
const specResponse = await fetch(specUrl);
if (!specResponse.ok) {
  throw new Error(`Failed to fetch OpenAPI spec from ${specUrl}: ${specResponse.status}`);
}
const specText = await specResponse.text();

const tmpDir = mkdtempSync(path.join(tmpdir(), "pricing-engine-openapi-"));
const specFile = path.join(tmpDir, "api-docs.json");
writeFileSync(specFile, specText);

try {
  execFileSync(
    "npx",
    ["--yes", "openapi-typescript-codegen", "-i", specFile, "-o", outDir, "-c", "fetch"],
    { stdio: "inherit" }
  );
} finally {
  rmSync(tmpDir, { recursive: true, force: true });
}

const openApiConfigPath = path.join(outDir, "core", "OpenAPI.ts");
const original = readFileSync(openApiConfigPath, "utf8");
const patched = original
  .replace(/BASE:\s*'[^']*'/, "BASE: ''")
  .replace(/WITH_CREDENTIALS:\s*false/, "WITH_CREDENTIALS: true");
writeFileSync(openApiConfigPath, patched);
