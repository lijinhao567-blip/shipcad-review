<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { MlCadViewer } from '@mlightcad/cad-viewer'
import { AcApSettingManager } from '@mlightcad/cad-simple-viewer'
import validSampleUrl from '../../../samples/dxf/valid_ship_section.dxf?url'
import DxfViewerPanel from './DxfViewerPanel.vue'

const file = shallowRef<File | null>(null)
const objectUrl = ref('')
const notes = ref<string[]>([])
const viewerMode = ref<'mlight' | 'dxf'>('dxf')

AcApSettingManager.instance.isShowCommandLine = false
AcApSettingManager.instance.isShowStats = true
AcApSettingManager.instance.isShowEntityInfo = true

const fileLabel = computed(() => file.value ? `${file.value.name} (${Math.round(file.value.size / 1024)} KB)` : '尚未选择文件')
const isDxf = computed(() => file.value?.name.toLowerCase().endsWith('.dxf') ?? false)

function selectFile(event: Event) {
  const target = event.target as HTMLInputElement
  const selected = target.files?.[0] ?? null
  setFile(selected, '文件选择器')
}

function loadSyntheticDxf() {
  const dxf = [
    '0', 'SECTION',
    '2', 'HEADER',
    '0', 'ENDSEC',
    '0', 'SECTION',
    '2', 'TABLES',
    '0', 'ENDSEC',
    '0', 'SECTION',
    '2', 'ENTITIES',
    '0', 'LINE',
    '8', 'S-STRUCTURE',
    '10', '0',
    '20', '0',
    '30', '0',
    '11', '100',
    '21', '100',
    '31', '0',
    '0', 'TEXT',
    '8', 'S-TEXT',
    '10', '10',
    '20', '10',
    '30', '0',
    '40', '5',
    '1', 'SHIPCAD POC',
    '0', 'ENDSEC',
    '0', 'EOF',
    ''
  ].join('\n')
  setFile(new File([dxf], 'shipcad_synthetic_poc.dxf', { type: 'application/dxf' }), '内置最小DXF')
}

async function loadProjectSample() {
  const response = await fetch(validSampleUrl)
  const blob = await response.blob()
  setFile(new File([blob], 'valid_ship_section.dxf', { type: 'application/dxf' }), '项目样例DXF')
}

function setFile(selected: File | null, source: string) {
  file.value = selected
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = selected ? URL.createObjectURL(selected) : ''
  notes.value = selected
    ? [`${source}：已选择 ${selected.name}`, 'cad-viewer 将通过 localFile prop 自动加载。']
    : []
}

function addNote(message: string) {
  notes.value = [...notes.value.slice(-8), message]
}
</script>

<template>
  <main class="shell">
    <section class="toolbar">
      <div>
        <p class="eyebrow">Experiment 1</p>
        <h1>CAD Viewer Integration POC</h1>
      </div>
      <label class="file-picker">
        <span>选择 DXF/DWG</span>
        <input type="file" accept=".dxf,.dwg" @change="selectFile" />
      </label>
      <button class="sample-button" @click="loadSyntheticDxf">加载内置最小DXF</button>
      <button class="sample-button" @click="loadProjectSample">加载项目样例DXF</button>
      <div class="mode-switch">
        <button :class="{ active: viewerMode === 'dxf' }" @click="viewerMode = 'dxf'">dxf-viewer</button>
        <button :class="{ active: viewerMode === 'mlight' }" @click="viewerMode = 'mlight'">mlightcad</button>
      </div>
    </section>

    <section class="layout">
      <aside class="panel">
        <h2>预研目标</h2>
        <ul>
          <li>加载项目样例 DXF/DWG。</li>
          <li>验证缩放、平移、图层展示。</li>
          <li>确认是否能按实体、图层或包围框高亮问题。</li>
          <li>确认能否嵌入主 Vue 前端。</li>
        </ul>
        <h2>当前文件</h2>
        <p>{{ fileLabel }}</p>
        <p v-if="objectUrl" class="url">{{ objectUrl }}</p>
        <h2>记录</h2>
        <p v-if="!notes.length" class="muted">等待选择文件。</p>
        <ul v-else>
          <li v-for="note in notes" :key="note">{{ note }}</li>
        </ul>
      </aside>

      <section class="viewer-shell">
        <DxfViewerPanel
          v-if="file && viewerMode === 'dxf' && isDxf"
          :url="objectUrl"
          @status="addNote"
        />
        <div v-else-if="file && viewerMode === 'dxf' && !isDxf" class="placeholder">
          <strong>dxf-viewer 仅支持 DXF</strong>
          <p>当前文件是 DWG。请切换到 mlightcad 或选择 DXF 文件。</p>
        </div>
        <MlCadViewer
          v-else-if="file && viewerMode === 'mlight'"
          class="cad-viewer"
          locale="zh"
          theme="dark"
          :background="0x1f2937"
          :local-file="file"
          :use-main-thread-draw="true"
          @create="addNote('mlightcad 已创建')"
          @destroy="addNote('mlightcad 已销毁')"
        />
        <div v-else class="placeholder">
          <strong>CAD Viewer 挂载区</strong>
          <p>选择 DXF 或 DWG 文件后，将在这里挂载 @mlightcad/cad-viewer。</p>
        </div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  background: #102a43;
  color: #fff;
}

.eyebrow {
  margin: 0 0 6px;
  color: #99f6e4;
  font-size: 13px;
}

h1 {
  margin: 0;
  font-size: 22px;
}

h2 {
  margin: 20px 0 8px;
  font-size: 16px;
}

.file-picker {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 6px;
  background: #0f766e;
  cursor: pointer;
}

.sample-button {
  border: 1px solid rgba(255,255,255,.28);
  border-radius: 6px;
  padding: 10px 14px;
  background: rgba(255,255,255,.12);
  color: #fff;
  cursor: pointer;
}

.sample-button:hover {
  background: rgba(255,255,255,.2);
}

.mode-switch {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid rgba(255,255,255,.22);
  border-radius: 8px;
  background: rgba(255,255,255,.08);
}

.mode-switch button {
  border: 0;
  border-radius: 6px;
  padding: 8px 10px;
  background: transparent;
  color: #cbd5e1;
  cursor: pointer;
}

.mode-switch button.active {
  background: #fff;
  color: #102a43;
}

.file-picker input {
  max-width: 240px;
}

.layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
}

.panel,
.viewer-shell {
  min-height: calc(100vh - 112px);
  border: 1px solid #d9e1ea;
  border-radius: 8px;
  background: #fff;
}

.panel {
  padding: 18px;
}

.panel p,
.panel li {
  color: #667085;
  line-height: 1.65;
}

.url {
  word-break: break-all;
}

.muted {
  color: #94a3b8;
}

.viewer-shell {
  overflow: hidden;
}

.cad-viewer {
  width: 100%;
  height: calc(100vh - 112px);
}

.placeholder {
  display: grid;
  place-items: center;
  max-width: 520px;
  min-height: calc(100vh - 112px);
  margin: 0 auto;
  padding: 24px;
  text-align: center;
  color: #667085;
}

.placeholder strong {
  display: block;
  margin-bottom: 8px;
  color: #172033;
  font-size: 20px;
}
</style>
