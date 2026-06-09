import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import DxfParser from '../frontend-vue/node_modules/dxf-viewer/src/parser/DxfParser.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const ROOT = path.resolve(__dirname, '..')
const DEFAULT_MANIFEST = path.join(ROOT, 'datasets', 'external', 'manifest.json')
const DEFAULT_CACHE = path.join(ROOT, '.run', 'external-dxf-candidates')

function resolveCachePath(cacheRoot, cacheFile) {
  const root = path.resolve(cacheRoot)
  const target = path.resolve(root, cacheFile)
  const relative = path.relative(root, target)
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`cacheFile escapes cache directory: ${cacheFile}`)
  }
  return target
}

function sha256Buffer(content) {
  return crypto.createHash('sha256').update(content).digest('hex')
}

function loadManifest(manifestPath) {
  const payload = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  if (!payload || !Array.isArray(payload.samples) || payload.samples.length === 0) {
    throw new Error(`${manifestPath} must contain a non-empty samples array`)
  }
  return payload
}

function validateSample(cacheRoot, sample) {
  const errors = []
  const sampleId = sample.id || '<missing-id>'
  const file = resolveCachePath(cacheRoot, sample.cacheFile)
  let content
  try {
    content = fs.readFileSync(file)
  } catch (error) {
    if (error && typeof error === 'object' && error.code === 'ENOENT') {
      return [`${sampleId}: cached DXF is missing; run tools/check_external_dxf_candidates.py first`]
    }
    return [`${sampleId}: cached DXF could not be read: ${error instanceof Error ? error.message : String(error)}`]
  }
  if (content.byteLength !== Number(sample.fileSize)) {
    errors.push(`${sampleId}: cached file size does not match manifest`)
  }
  if (sha256Buffer(content) !== sample.sha256) {
    errors.push(`${sampleId}: cached sha256 does not match manifest`)
  }

  let dxf
  try {
    dxf = new DxfParser().parseSync(content.toString('utf8'))
  } catch (error) {
    return [...errors, `${sampleId}: dxf-viewer parser failed: ${error instanceof Error ? error.message : String(error)}`]
  }
  if (!dxf || !Array.isArray(dxf.entities)) {
    return [...errors, `${sampleId}: dxf-viewer parser returned no entities array`]
  }

  const expectations = sample.previewExpectations ?? {}
  const entityCount = dxf.entities.length
  const minimum = Number(expectations.minDxfViewerEntities ?? 1)
  if (entityCount < minimum) {
    errors.push(`${sampleId}: dxf-viewer entity count ${entityCount} < ${minimum}`)
  }

  const layers = new Set(Object.keys(dxf.tables?.layer?.layers ?? {}))
  const minimumLayers = Number(expectations.minLayerCount ?? 1)
  if (layers.size < minimumLayers) {
    errors.push(`${sampleId}: dxf-viewer layer count ${layers.size} < ${minimumLayers}`)
  }
  for (const layer of expectations.requiredLayers ?? []) {
    if (!layers.has(layer)) errors.push(`${sampleId}: dxf-viewer missing required layer ${layer}`)
  }

  const types = new Set(dxf.entities.map((entity) => entity.type))
  for (const entityType of expectations.requiredEntityTypes ?? []) {
    if (!types.has(entityType)) errors.push(`${sampleId}: dxf-viewer missing required entity type ${entityType}`)
  }
  return errors
}

function main() {
  const manifestPath = path.resolve(process.argv[2] ?? DEFAULT_MANIFEST)
  const cacheRoot = path.resolve(process.argv[3] ?? DEFAULT_CACHE)
  const manifest = loadManifest(manifestPath)
  const errors = manifest.samples.flatMap((sample) => validateSample(cacheRoot, sample))
  if (errors.length > 0) {
    console.error('External dxf-viewer candidate validation failed:')
    for (const error of errors) console.error(`- ${error}`)
    process.exit(1)
  }
  console.log(`External dxf-viewer candidates: ${manifest.samples.length} samples parsed`)
}

main()
