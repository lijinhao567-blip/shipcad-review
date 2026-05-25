<script setup lang="ts">
import { Color } from 'three'
import { DxfViewer, type LayerInfo } from 'dxf-viewer'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ReviewIssue } from '../api'

const props = defineProps<{
  fileUrl: string
  issues: ReviewIssue[]
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

let viewer: DxfViewer | null = null
let loadSeq = 0

const issueLayers = computed(() => new Set(props.issues.map((issue) => issue.layerName).filter(Boolean)))
const selectedLayer = computed(() => props.issues.find((issue) => issue.id === props.selectedIssueId)?.layerName ?? '')

function layerColor(color: number): string {
  const normalized = Math.max(0, Math.min(0xffffff, color))
  return `#${normalized.toString(16).padStart(6, '0')}`
}

function errorMessage(value: unknown): string {
  return value instanceof Error ? value.message : String(value)
}

async function load(url: string) {
  if (!viewer || !url) return
  const seq = ++loadSeq
  error.value = ''
  layers.value = []
  bounds.value = null
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
    status.value = `dxf-viewer 加载完成：${layers.value.length} 个图层`
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

onMounted(async () => {
  if (!host.value) return
  viewer = new DxfViewer(host.value, {
    autoResize: true,
    clearColor: new Color('#ffffff'),
    clearAlpha: 1,
    colorCorrection: true,
    retainParsedDxf: true
  })
  viewer.Subscribe('message', (event) => {
    const message = typeof event === 'object' && event ? event.message ?? JSON.stringify(event) : String(event)
    status.value = `dxf-viewer: ${message}`
  })
  await load(props.fileUrl)
})

watch(() => props.fileUrl, load)

onBeforeUnmount(() => {
  loadSeq += 1
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
      </div>
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
  display: flex;
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
