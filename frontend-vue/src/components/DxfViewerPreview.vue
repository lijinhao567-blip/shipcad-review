<script setup lang="ts">
import { BufferGeometry, Color, Group, Line, LineBasicMaterial, Vector3 } from 'three'
import { DxfViewer, type LayerInfo } from 'dxf-viewer'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { EvidenceBounds, EvidenceLocation, ParsedEntity, ReviewEvidence, ReviewIssue } from '../api'

const props = defineProps<{
  fileUrl: string
  issues: ReviewIssue[]
  entities: ParsedEntity[]
  selectedIssueId: string
}>()

const emit = defineEmits<{
  loadFailed: [message: string]
}>()

const host = ref<HTMLDivElement | null>(null)
const status = ref('等待加载DXF')
const error = ref('')
const layers = ref<LayerInfo[]>([])
const bounds = ref<ReturnType<DxfViewer['GetBounds']> | null>(null)
const loaded = ref(false)

let viewer: DxfViewer | null = null
let highlightGroup: Group | null = null
let loadSeq = 0

type FocusBounds = EvidenceBounds & {
  source: 'evidence' | 'entity' | 'layer'
}

const issueLayers = computed(() => new Set(props.issues.map((issue) => issue.layerName).filter((layer): layer is string => Boolean(layer))))
const selectedIssue = computed(() => props.issues.find((issue) => issue.id === props.selectedIssueId))
const selectedLayer = computed(() => selectedIssue.value?.layerName ?? '')
const entityById = computed(() => new Map(props.entities.map((entity) => [entity.id, entity])))
const selectedFocusBounds = computed(() => selectedIssue.value ? focusBoundsForIssue(selectedIssue.value) : null)
const selectedFocusLabel = computed(() => {
  const target = selectedFocusBounds.value
  if (!target) return ''
  const source = target.source === 'evidence' ? '证据范围' : target.source === 'entity' ? '图元范围' : '图层范围'
  return `${source}：${formatCoordinate(target.minX)},${formatCoordinate(target.minY)} - ${formatCoordinate(target.maxX)},${formatCoordinate(target.maxY)}`
})

function layerColor(color: number): string {
  const normalized = Math.max(0, Math.min(0xffffff, color))
  return `#${normalized.toString(16).padStart(6, '0')}`
}

function errorMessage(value: unknown): string {
  return value instanceof Error ? value.message : String(value)
}

// Keep normal preview fast; only automated smoke needs a readable WebGL buffer.
function preserveDrawingBufferForSmoke(): boolean {
  return new URLSearchParams(window.location.search).has('dxf-preview-smoke')
}

async function load(url: string) {
  if (!viewer || !url) return
  const seq = ++loadSeq
  error.value = ''
  layers.value = []
  bounds.value = null
  loaded.value = false
  clearHighlight()
  status.value = 'dxf-viewer 正在加载'
  try {
    viewer.Clear()
    await viewer.Load({
      url,
      progressCbk: (phase, processed, total) => {
        if (seq === loadSeq) {
          status.value = `dxf-viewer ${phase}: ${processed}${total ? `/${total}` : ''}`
        }
      }
    })
    if (seq !== loadSeq) return
    layers.value = Array.from(viewer.GetLayers())
    bounds.value = viewer.GetBounds()
    loaded.value = true
    status.value = `dxf-viewer 加载完成：${layers.value.length} 个图层`
    focusSelectedIssue('auto')
  } catch (reason) {
    if (seq !== loadSeq) return
    const message = errorMessage(reason)
    error.value = message
    status.value = 'dxf-viewer 加载失败'
    emit('loadFailed', message)
  }
}

function showAllLayers() {
  if (!viewer) return
  for (const layer of layers.value) viewer.ShowLayer(layer.name, true)
  viewer.Render()
  status.value = '已显示全部图层'
}

function showIssueLayersOnly() {
  if (!viewer || issueLayers.value.size === 0) return
  for (const layer of layers.value) viewer.ShowLayer(layer.name, issueLayers.value.has(layer.name))
  viewer.Render()
  status.value = `只显示 ${issueLayers.value.size} 个问题图层`
}

function focusSelectedIssue(mode: 'auto' | 'manual') {
  if (!viewer || !loaded.value) return
  const target = selectedFocusBounds.value
  if (!target) {
    clearHighlight()
    if (mode === 'manual' && props.selectedIssueId) status.value = '当前问题暂无可聚焦CAD范围'
    return
  }
  const sceneBounds = sceneBoundsFromCad(target)
  if (!sceneBounds) return
  if (selectedLayer.value) viewer.ShowLayer(selectedLayer.value, true)
  viewer.FitView(sceneBounds.minX, sceneBounds.maxX, sceneBounds.minY, sceneBounds.maxY, 0.35)
  drawHighlight(sceneBounds)
  viewer.Render()
  status.value = `已聚焦问题：${selectedIssue.value?.ruleCode ?? props.selectedIssueId}`
}

function drawHighlight(target: EvidenceBounds) {
  if (!viewer) return
  clearHighlight()
  const padded = expandBounds(target, 0.08)
  const points = [
    new Vector3(padded.minX, padded.minY, 0.02),
    new Vector3(padded.maxX, padded.minY, 0.02),
    new Vector3(padded.maxX, padded.maxY, 0.02),
    new Vector3(padded.minX, padded.maxY, 0.02),
    new Vector3(padded.minX, padded.minY, 0.02)
  ]
  const geometry = new BufferGeometry().setFromPoints(points)
  const material = new LineBasicMaterial({
    color: 0xdc2626,
    depthTest: false,
    depthWrite: false,
    transparent: true,
    opacity: 0.95
  })
  const line = new Line(geometry, material)
  line.renderOrder = 999
  highlightGroup = new Group()
  highlightGroup.name = 'shipcad-selected-issue-highlight'
  highlightGroup.add(line)
  viewer.GetScene().add(highlightGroup)
}

function clearHighlight() {
  if (!viewer || !highlightGroup) return
  viewer.GetScene().remove(highlightGroup)
  for (const child of highlightGroup.children) {
    if (child instanceof Line) {
      child.geometry.dispose()
      if (Array.isArray(child.material)) {
        child.material.forEach((material) => material.dispose())
      } else {
        child.material.dispose()
      }
    }
  }
  highlightGroup = null
}

function focusBoundsForIssue(issue: ReviewIssue): FocusBounds | null {
  for (const evidence of issue.evidences ?? []) {
    const bounds = boundsFromEvidence(evidence)
    if (bounds) return { ...bounds, source: 'evidence' }
  }
  if (issue.entityRef) {
    const entityBounds = boundsFromEntity(entityById.value.get(issue.entityRef))
    if (entityBounds) return { ...entityBounds, source: 'entity' }
  }
  if (issue.layerName) {
    const layerBounds = boundsFromLayer(issue.layerName)
    if (layerBounds) return { ...layerBounds, source: 'layer' }
  }
  return null
}

function boundsFromEvidence(evidence: ReviewEvidence): EvidenceBounds | null {
  const location = evidence.location
  if (!location) return null
  if (location.coordinateSpace === 'CAD_MODEL') return normalizeBounds(location.bounds)
  if (location.coordinateSpace === 'RASTER_IMAGE') return rasterBoundsToCad(location)
  return null
}

function rasterBoundsToCad(location: EvidenceLocation): EvidenceBounds | null {
  if (!location.bounds || !location.transform || location.transform.targetSpace !== 'CAD_MODEL') return null
  const { sourceBounds, targetBounds, sourceOrigin, targetOrigin } = location.transform
  const leftTop = mapPoint(location.bounds.minX, location.bounds.minY, sourceBounds, targetBounds, sourceOrigin, targetOrigin)
  const rightBottom = mapPoint(location.bounds.maxX, location.bounds.maxY, sourceBounds, targetBounds, sourceOrigin, targetOrigin)
  return normalizeBounds({
    minX: Math.min(leftTop.x, rightBottom.x),
    minY: Math.min(leftTop.y, rightBottom.y),
    maxX: Math.max(leftTop.x, rightBottom.x),
    maxY: Math.max(leftTop.y, rightBottom.y)
  })
}

function mapPoint(
  x: number,
  y: number,
  source: EvidenceBounds,
  target: EvidenceBounds,
  sourceOrigin: string,
  targetOrigin: string
) {
  const sourceWidth = Math.max(1, source.maxX - source.minX)
  const sourceHeight = Math.max(1, source.maxY - source.minY)
  const targetWidth = target.maxX - target.minX
  const targetHeight = target.maxY - target.minY
  const nx = (x - source.minX) / sourceWidth
  const ny = (y - source.minY) / sourceHeight
  const flipY = sourceOrigin === 'TOP_LEFT' && targetOrigin === 'BOTTOM_LEFT'
  return {
    x: target.minX + nx * targetWidth,
    y: flipY ? target.maxY - ny * targetHeight : target.minY + ny * targetHeight
  }
}

function boundsFromEntity(entity: ParsedEntity | undefined): EvidenceBounds | null {
  if (!entity) return null
  const geometryBounds = normalizeBounds(entity.geometry?.bounds)
  if (geometryBounds) return geometryBounds
  if (entity.x != null && entity.y != null) {
    return { minX: entity.x, minY: entity.y, maxX: entity.x, maxY: entity.y }
  }
  return null
}

function boundsFromLayer(layerName: string): EvidenceBounds | null {
  const layerBounds = props.entities
    .filter((entity) => entity.layerName === layerName)
    .map(boundsFromEntity)
    .filter((value): value is EvidenceBounds => Boolean(value))
  return unionBounds(layerBounds)
}

function sceneBoundsFromCad(target: EvidenceBounds): EvidenceBounds | null {
  if (!viewer) return null
  const origin = viewer.GetOrigin()
  return expandBounds({
    minX: target.minX - origin.x,
    maxX: target.maxX - origin.x,
    minY: target.minY - origin.y,
    maxY: target.maxY - origin.y
  })
}

function normalizeBounds(value: unknown): EvidenceBounds | null {
  if (!value || typeof value !== 'object') return null
  const candidate = value as Partial<EvidenceBounds>
  const minX = numberValue(candidate.minX)
  const minY = numberValue(candidate.minY)
  const maxX = numberValue(candidate.maxX)
  const maxY = numberValue(candidate.maxY)
  if (minX == null || minY == null || maxX == null || maxY == null) return null
  return {
    minX: Math.min(minX, maxX),
    minY: Math.min(minY, maxY),
    maxX: Math.max(minX, maxX),
    maxY: Math.max(minY, maxY)
  }
}

function unionBounds(values: EvidenceBounds[]): EvidenceBounds | null {
  if (!values.length) return null
  return {
    minX: Math.min(...values.map((value) => value.minX)),
    minY: Math.min(...values.map((value) => value.minY)),
    maxX: Math.max(...values.map((value) => value.maxX)),
    maxY: Math.max(...values.map((value) => value.maxY))
  }
}

function expandBounds(value: EvidenceBounds, ratio = 0): EvidenceBounds {
  const width = Math.max(value.maxX - value.minX, 1)
  const height = Math.max(value.maxY - value.minY, 1)
  const pad = Math.max(width, height) * ratio
  const centerX = (value.minX + value.maxX) / 2
  const centerY = (value.minY + value.maxY) / 2
  return {
    minX: centerX - width / 2 - pad,
    maxX: centerX + width / 2 + pad,
    minY: centerY - height / 2 - pad,
    maxY: centerY + height / 2 + pad
  }
}

function numberValue(value: unknown): number | null {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

function formatCoordinate(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

onMounted(async () => {
  if (!host.value) return
  viewer = new DxfViewer(host.value, {
    autoResize: true,
    clearColor: new Color('#ffffff'),
    clearAlpha: 1,
    colorCorrection: true,
    preserveDrawingBuffer: preserveDrawingBufferForSmoke(),
    retainParsedDxf: true
  })
  viewer.Subscribe('message', (event) => {
    const message = typeof event === 'object' && event ? event.message ?? JSON.stringify(event) : String(event)
    status.value = `dxf-viewer: ${message}`
  })
  await load(props.fileUrl)
})

watch(() => props.fileUrl, load)
watch(() => [props.selectedIssueId, props.issues, props.entities], () => focusSelectedIssue('auto'), { deep: true })

onBeforeUnmount(() => {
  loadSeq += 1
  clearHighlight()
  viewer?.Destroy()
  viewer = null
})
</script>

<template>
  <div class="dxf-viewer-panel">
    <div ref="host" class="dxf-webgl-host"></div>
    <aside class="dxf-meta">
      <strong>DXF WebGL预览</strong>
      <span>{{ status }}</span>
      <span v-if="bounds">范围：{{ Math.round(bounds.minX) }},{{ Math.round(bounds.minY) }} - {{ Math.round(bounds.maxX) }},{{ Math.round(bounds.maxY) }}</span>
      <div class="viewer-tools">
        <button type="button" @click="showAllLayers">全部图层</button>
        <button type="button" :disabled="issueLayers.size === 0" @click="showIssueLayersOnly">问题图层</button>
        <button type="button" :disabled="!selectedFocusBounds" @click="focusSelectedIssue('manual')">聚焦问题</button>
      </div>
      <span v-if="selectedFocusLabel" class="focus-hint">{{ selectedFocusLabel }}</span>
      <span v-else-if="selectedIssueId" class="focus-hint muted">当前问题暂无CAD范围</span>
      <div v-for="layer in layers" :key="layer.name" class="layer-row" :class="{ marked: issueLayers.has(layer.name), selected: selectedLayer === layer.name }">
        <i :style="{ backgroundColor: layerColor(layer.color) }"></i>
        <span>{{ layer.displayName || layer.name }}</span>
      </div>
    </aside>
    <div v-if="error" class="viewer-error">
      <strong>正式预览加载失败</strong>
      <span>{{ error }}</span>
    </div>
  </div>
</template>

<style scoped>
.dxf-viewer-panel {
  position: relative;
  width: 100%;
  height: 520px;
  overflow: hidden;
  border: 1px solid #d9e1ea;
  border-radius: 8px;
  background: #fff;
}

.dxf-webgl-host {
  width: 100%;
  height: 100%;
}

.dxf-meta {
  position: absolute;
  top: 12px;
  right: 12px;
  display: grid;
  gap: 7px;
  width: min(260px, calc(100% - 24px));
  max-height: calc(100% - 24px);
  overflow: auto;
  border: 1px solid #d9e1ea;
  border-radius: 8px;
  padding: 12px;
  background: rgba(255,255,255,.94);
  color: #172033;
  font-size: 13px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, .10);
}

.dxf-meta span {
  color: #667085;
}

.viewer-tools {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.viewer-tools button {
  flex: 1;
  padding: 7px 8px;
  font-size: 12px;
}

.viewer-tools button:disabled {
  opacity: .45;
  cursor: not-allowed;
}

.focus-hint {
  border: 1px solid #fecaca;
  border-radius: 6px;
  padding: 6px 8px;
  background: #fff5f5;
  color: #b42318;
  font-size: 12px;
  line-height: 1.45;
}

.focus-hint.muted {
  border-color: #d9e1ea;
  background: #f8fafc;
  color: #667085;
}

.layer-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  border-radius: 6px;
  padding: 5px 6px;
}

.layer-row.marked {
  background: #fff7ed;
}

.layer-row.selected {
  outline: 2px solid #dc2626;
}

.layer-row i {
  width: 10px;
  height: 10px;
  border: 1px solid #d9e1ea;
  border-radius: 999px;
  flex: 0 0 auto;
}

.layer-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer-error {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 16px;
  display: grid;
  gap: 4px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 12px;
  background: #fff5f5;
  color: #b42318;
}

.viewer-error span {
  color: #7f1d1d;
  word-break: break-word;
}
</style>
