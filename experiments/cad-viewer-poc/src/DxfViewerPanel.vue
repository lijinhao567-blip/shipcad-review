<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Color } from 'three'
import { DxfViewer, type LayerInfo } from 'dxf-viewer'

const props = defineProps<{
  url: string
}>()

const emit = defineEmits<{
  status: [message: string]
}>()

const host = ref<HTMLDivElement | null>(null)
const layers = ref<LayerInfo[]>([])
const bounds = ref<ReturnType<DxfViewer['GetBounds']> | null>(null)
let viewer: DxfViewer | null = null

async function load(url: string) {
  if (!viewer || !url) return
  emit('status', 'dxf-viewer 开始加载')
  await viewer.Load({
    url,
    progressCbk: (phase, processed, total) => {
      emit('status', `dxf-viewer ${phase}: ${processed}${total ? `/${total}` : ''}`)
    }
  })
  layers.value = Array.from(viewer.GetLayers())
  bounds.value = viewer.GetBounds()
  emit('status', `dxf-viewer 加载完成：${layers.value.length} 个图层`)
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
  viewer.Subscribe('message', (event) => emit('status', `dxf-viewer message: ${event.message ?? event}`))
  await load(props.url)
})

watch(() => props.url, async (url) => {
  if (viewer && url) {
    await load(url)
  }
})

onBeforeUnmount(() => {
  viewer?.Destroy()
  viewer = null
})
</script>

<template>
  <div class="dxf-panel">
    <div ref="host" class="dxf-host"></div>
    <aside class="dxf-meta">
      <strong>DXF Viewer</strong>
      <span>{{ layers.length }} layers</span>
      <span v-if="bounds">bounds: {{ Math.round(bounds.minX) }},{{ Math.round(bounds.minY) }} - {{ Math.round(bounds.maxX) }},{{ Math.round(bounds.maxY) }}</span>
      <div v-for="layer in layers" :key="layer.name" class="layer-row">
        <i :style="{ backgroundColor: `#${layer.color.toString(16).padStart(6, '0')}` }"></i>
        <span>{{ layer.displayName || layer.name }}</span>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.dxf-panel {
  position: relative;
  width: 100%;
  height: calc(100vh - 112px);
  overflow: hidden;
  background: #fff;
}

.dxf-host {
  width: 100%;
  height: 100%;
}

.dxf-meta {
  position: absolute;
  top: 12px;
  right: 12px;
  display: grid;
  gap: 7px;
  width: 220px;
  max-height: calc(100% - 24px);
  overflow: auto;
  border: 1px solid #d9e1ea;
  border-radius: 8px;
  padding: 12px;
  background: rgba(255,255,255,.94);
  color: #172033;
  font-size: 13px;
}

.dxf-meta span {
  color: #667085;
}

.layer-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.layer-row i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: 1px solid #d9e1ea;
  flex: 0 0 auto;
}

.layer-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
