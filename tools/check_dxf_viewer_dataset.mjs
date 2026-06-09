import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import DxfParser from '../frontend-vue/node_modules/dxf-viewer/src/parser/DxfParser.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const ROOT = path.resolve(__dirname, '..')
const DEFAULT_MANIFEST = path.join(ROOT, 'datasets', 'parser', 'manifest.json')

function loadManifest(manifestPath) {
  const payload = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  if (!payload || !Array.isArray(payload.samples) || payload.samples.length === 0) {
    throw new Error(`${manifestPath} must contain a non-empty samples array`)
  }
  return payload
}

function layerNames(dxf) {
  return new Set(Object.keys(dxf.tables?.layer?.layers ?? {}))
}

function blockNames(dxf) {
  return new Set(Object.keys(dxf.blocks ?? {}))
}

function validateSample(manifestDir, sample) {
  const errors = []
  const sampleId = sample.id || '<missing-id>'
  const file = sample.file
  if (typeof file !== 'string') return [`${sampleId}: file must be a string`]
  const dxfPath = path.resolve(manifestDir, file)
  if (!fs.existsSync(dxfPath)) return [`${sampleId}: file does not exist: ${file}`]

  let dxf
  try {
    dxf = new DxfParser().parseSync(fs.readFileSync(dxfPath, 'utf8'))
  } catch (error) {
    return [`${sampleId}: dxf-viewer parser failed: ${error instanceof Error ? error.message : String(error)}`]
  }
  if (!dxf || !Array.isArray(dxf.entities)) {
    return [`${sampleId}: dxf-viewer parser returned no entities array`]
  }

  const parserExpectations = sample.parserExpectations ?? {}
  const previewExpectations = sample.previewExpectations ?? {}
  const entityCount = dxf.entities.length
  const minEntities = Number(previewExpectations.minDxfViewerEntities ?? parserExpectations.minEntityCount ?? 1)
  if (entityCount < minEntities) {
    errors.push(`${sampleId}: dxf-viewer entity count ${entityCount} < ${minEntities}`)
  }

  const layers = layerNames(dxf)
  const minLayerCount = Number(previewExpectations.minLayerCount ?? 1)
  if (layers.size < minLayerCount) {
    errors.push(`${sampleId}: dxf-viewer layer count ${layers.size} < ${minLayerCount}`)
  }
  for (const layer of parserExpectations.requiredLayers ?? []) {
    if (!layers.has(layer)) errors.push(`${sampleId}: dxf-viewer missing required layer ${layer}`)
  }

  const blocks = blockNames(dxf)
  for (const block of parserExpectations.requiredBlocks ?? []) {
    if (!blocks.has(block)) errors.push(`${sampleId}: dxf-viewer missing required block ${block}`)
  }

  const types = new Set(dxf.entities.map((entity) => entity.type))
  for (const entityType of parserExpectations.requiredEntityTypes ?? []) {
    if (!types.has(entityType)) errors.push(`${sampleId}: dxf-viewer missing required entity type ${entityType}`)
  }
  return errors
}

function main() {
  const manifestPath = path.resolve(process.argv[2] ?? DEFAULT_MANIFEST)
  const manifest = loadManifest(manifestPath)
  const manifestDir = path.dirname(manifestPath)
  const errors = manifest.samples.flatMap((sample) => validateSample(manifestDir, sample))
  if (errors.length > 0) {
    console.error('dxf-viewer dataset validation failed:')
    for (const error of errors) console.error(`- ${error}`)
    process.exit(1)
  }
  console.log(`dxf-viewer dataset: ${manifest.samples.length} samples parsed`)
}

main()
