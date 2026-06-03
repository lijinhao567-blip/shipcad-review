<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api, type Dashboard, type Drawing, type DrawingVersion, type ParsedEntity, type Project, type ReportDocument, type ReviewEvidence, type ReviewIssue, type ReviewTask } from './api'

const DxfViewerPreview = defineAsyncComponent(() => import('./components/DxfViewerPreview.vue'))
const DxfCanvasDiagnostics = defineAsyncComponent(() => import('./components/DxfCanvas.vue'))

const tab = ref<'dashboard' | 'projects' | 'issues' | 'reports'>('dashboard')
const loginState = reactive({ username: 'admin', password: 'admin123', label: '未登录' })
const projectForm = reactive({ name: 'A22船舶审图试点', shipNo: 'S-2026-001', owner: '设计院审图组', description: 'DXF优先的AI辅助审图试点项目' })
const drawingForm = reactive({ projectId: '', drawingNo: 'A22-SEC-001', title: '船体分段结构图', discipline: '船体结构' })
const uploadForm = reactive({ drawingId: '', versionNo: 'V1', file: null as File | null })
const selectedReviewVersion = ref('')
const selectedTask = ref('')
const previewVersionId = ref('')
const previewFileUrl = ref('')
const previewFileError = ref('')
const selectedIssueId = ref('')
const showCanvasDiagnostics = ref(false)
const compare = reactive({ leftId: '', rightId: '' })
const output = ref('')
const reportDocument = ref<ReportDocument | null>(null)
const reportContent = ref('')
const reportActionMessage = ref('')
let previewFileRequestId = 0
let previewFileVersionId = ''

type ReportBlock =
  | { type: 'paragraph'; text: string }
  | { type: 'list'; items: string[] }
  | { type: 'table'; headers: string[]; rows: string[][] }
  | { type: 'subheading'; text: string }

type ReportSection = {
  title: string
  level: number
  blocks: ReportBlock[]
  rawLines: string[]
}

const dashboard = ref<Dashboard | null>(null)
const projects = ref<Project[]>([])
const drawings = ref<Drawing[]>([])
const versions = ref<DrawingVersion[]>([])
const tasks = ref<ReviewTask[]>([])
const issues = ref<ReviewIssue[]>([])
const entities = ref<ParsedEntity[]>([])
const loading = ref(false)

const selectedDrawingVersions = computed(() => versions.value)
const previewIssues = computed(() => issues.value.filter((issue) => !previewVersionId.value || issue.versionId === previewVersionId.value))
const previewVersion = computed(() => versions.value.find((item) => item.id === previewVersionId.value))
const isDxfPreview = computed(() => previewVersion.value?.fileName.toLowerCase().endsWith('.dxf') ?? false)
const entityById = computed(() => new Map(entities.value.map((entity) => [entity.id, entity])))
const selectedIssue = computed(() => issues.value.find((issue) => issue.id === selectedIssueId.value))
const selectedReportTask = computed(() => tasks.value.find((task) => task.id === selectedTask.value))
const selectedReportVersion = computed(() => versions.value.find((version) => version.id === selectedReportTask.value?.versionId))
const selectedReportIssues = computed(() => issues.value.filter((issue) => issue.taskId === selectedTask.value))
const reportSections = computed(() => parseReportSections(reportContent.value))
const reportTitle = computed(() => reportSections.value.find((section) => section.level === 1)?.title ?? '审查报告')
const reportBodySections = computed(() => reportSections.value.filter((section) => section.level === 2))
const reportSummary = computed(() => selectedReportVersion.value ? parseSummary(selectedReportVersion.value) : {})
const reportEntityEvidenceCount = computed(() => selectedReportIssues.value.filter((issue) => issue.entityRef).length)
const reportLayerEvidenceCount = computed(() => selectedReportIssues.value.filter((issue) => !issue.entityRef && issue.layerName).length)
const reportStructuredEvidenceCount = computed(() => selectedReportIssues.value.reduce((sum, issue) => sum + (issue.evidences?.length ?? 0), 0))
const reportHighIssueCount = computed(() => selectedReportIssues.value.filter((issue) => issue.severity === 'HIGH').length)

function versionLabel(version: DrawingVersion): string {
  const drawing = drawings.value.find((item) => item.id === version.drawingId)
  return `${drawing?.drawingNo ?? '图纸'} ${version.versionNo}`
}

function parseSummary(version: DrawingVersion): Record<string, unknown> {
  try {
    return JSON.parse(version.parseSummaryJson || '{}')
  } catch {
    return {}
  }
}

function summaryNumber(key: string): number {
  const value = reportSummary.value[key]
  return typeof value === 'number' ? value : 0
}

function parseReportSections(markdown: string): ReportSection[] {
  const sections: Array<ReportSection & { pendingLines: string[] }> = []
  let current: (ReportSection & { pendingLines: string[] }) | null = null
  for (const line of markdown.split(/\r?\n/)) {
    const heading = /^(#{1,2})\s+(.+)$/.exec(line)
    if (heading) {
      current = { title: heading[2].trim(), level: heading[1].length, rawLines: [], pendingLines: [], blocks: [] }
      sections.push(current)
      continue
    }
    if (current) {
      current.rawLines.push(line)
      current.pendingLines.push(line)
    }
  }
  return sections.map((section) => ({
    title: section.title,
    level: section.level,
    rawLines: section.rawLines,
    blocks: parseReportBlocks(section.pendingLines)
  }))
}

function parseReportBlocks(lines: string[]): ReportBlock[] {
  const blocks: ReportBlock[] = []
  let index = 0
  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim()) {
      index += 1
      continue
    }
    if (line.startsWith('### ')) {
      blocks.push({ type: 'subheading', text: line.replace(/^###\s+/, '').trim() })
      index += 1
      continue
    }
    if (line.trim().startsWith('|')) {
      const tableLines: string[] = []
      while (index < lines.length && lines[index].trim().startsWith('|')) {
        tableLines.push(lines[index])
        index += 1
      }
      const table = parseReportTable(tableLines)
      if (table) blocks.push(table)
      continue
    }
    if (line.trim().startsWith('- ')) {
      const items: string[] = []
      while (index < lines.length && lines[index].trim().startsWith('- ')) {
        items.push(formatReportText(lines[index].trim().slice(2)))
        index += 1
      }
      blocks.push({ type: 'list', items })
      continue
    }
    const paragraph: string[] = []
    while (
      index < lines.length
      && lines[index].trim()
      && !lines[index].startsWith('### ')
      && !lines[index].trim().startsWith('|')
      && !lines[index].trim().startsWith('- ')
    ) {
      paragraph.push(lines[index].trim())
      index += 1
    }
    blocks.push({ type: 'paragraph', text: formatReportText(paragraph.join(' ')) })
  }
  return blocks
}

function parseReportTable(lines: string[]): ReportBlock | null {
  if (lines.length < 2) return null
  const headers = splitMarkdownRow(lines[0])
  const rows = lines.slice(isTableDivider(lines[1]) ? 2 : 1).map(splitMarkdownRow)
  return { type: 'table', headers, rows }
}

function splitMarkdownRow(line: string): string[] {
  let value = line.trim()
  if (value.startsWith('|')) value = value.slice(1)
  if (value.endsWith('|')) value = value.slice(0, -1)
  const cells: string[] = []
  let current = ''
  let escaped = false
  for (const char of value) {
    if (char === '|' && !escaped) {
      cells.push(formatReportText(current.trim()))
      current = ''
      continue
    }
    if (char === '\\' && !escaped) {
      escaped = true
      current += char
      continue
    }
    escaped = false
    current += char
  }
  cells.push(formatReportText(current.trim()))
  return cells
}

function isTableDivider(line: string): boolean {
  return /^\|?[\s:-]+\|[\s|:-]*$/.test(line.trim())
}

function formatReportText(value: string): string {
  return value.replace(/\\\|/g, '|').replace(/<br\s*\/?>/gi, '\n')
}

function taskLabel(task: ReviewTask): string {
  return `${task.id} / ${task.status} / ${task.issueCount}个问题`
}

function taskVersionLabel(task: ReviewTask): string {
  const version = versions.value.find((item) => item.id === task.versionId)
  return version ? versionLabel(version) : task.versionId
}

async function refreshAll() {
  if (!api.token) return
  loading.value = true
  try {
    const [d, p, dr, v, t, i] = await Promise.all([
      api.request<Dashboard>('/api/dashboard'),
      api.request<Project[]>('/api/projects'),
      api.request<Drawing[]>('/api/drawings'),
      api.request<DrawingVersion[]>('/api/versions'),
      api.request<ReviewTask[]>('/api/review-tasks'),
      api.request<ReviewIssue[]>('/api/issues')
    ])
    dashboard.value = d
    projects.value = p
    drawings.value = dr
    versions.value = v
    tasks.value = t
    issues.value = i
    drawingForm.projectId ||= p[0]?.id ?? ''
    uploadForm.drawingId ||= dr[0]?.id ?? ''
    selectedReviewVersion.value ||= v[0]?.id ?? ''
    previewVersionId.value ||= v[0]?.id ?? ''
    selectedTask.value ||= t[0]?.id ?? ''
    compare.leftId ||= v[0]?.id ?? ''
    compare.rightId ||= v[1]?.id ?? v[0]?.id ?? ''
    await refreshPreview()
  } finally {
    loading.value = false
  }
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function waitForTask(taskId: string) {
  for (let index = 0; index < 40; index += 1) {
    await delay(1200)
    await refreshAll()
    const task = tasks.value.find((item) => item.id === taskId)
    if (task?.status === 'FINISHED' || task?.status === 'FAILED') return
  }
}

async function refreshEntities() {
  if (!previewVersionId.value || !api.token) {
    entities.value = []
    return
  }
  entities.value = await api.request<ParsedEntity[]>(`/api/versions/${previewVersionId.value}/entities`)
}

function messageOf(value: unknown): string {
  return value instanceof Error ? value.message : String(value)
}

function releasePreviewFileUrl() {
  if (previewFileUrl.value) URL.revokeObjectURL(previewFileUrl.value)
  previewFileUrl.value = ''
  previewFileVersionId = ''
}

async function refreshPreviewFile(force = false) {
  const requestId = ++previewFileRequestId
  const version = previewVersion.value
  if (!force && version && previewFileUrl.value && previewFileVersionId === version.id) return
  releasePreviewFileUrl()
  previewFileError.value = ''
  if (!previewVersionId.value || !api.token || !version) return
  if (!isDxfPreview.value) {
    previewFileError.value = '正式预览当前仅支持DXF；DWG需要先通过LibreDWG/ODA转换生成DXF预览文件。'
    return
  }
  try {
    const blob = await api.blob(`/api/versions/${previewVersionId.value}/file`)
    if (requestId !== previewFileRequestId) return
    previewFileUrl.value = URL.createObjectURL(blob)
    previewFileVersionId = version.id
  } catch (reason) {
    if (requestId !== previewFileRequestId) return
    previewFileError.value = messageOf(reason)
  }
}

async function refreshPreview(resetDiagnostics = false) {
  if (resetDiagnostics) showCanvasDiagnostics.value = false
  await Promise.all([refreshEntities(), refreshPreviewFile(resetDiagnostics)])
}

async function login() {
  const result = await api.login(loginState.username, loginState.password)
  loginState.label = `已登录：${result.user.displayName}`
  await refreshAll()
}

async function createProject() {
  await api.request('/api/projects', { method: 'POST', body: JSON.stringify(projectForm) })
  await refreshAll()
}

async function createDrawing() {
  await api.request('/api/drawings', { method: 'POST', body: JSON.stringify(drawingForm) })
  await refreshAll()
}

async function uploadVersion() {
  if (!uploadForm.file) throw new Error('请选择DXF或DWG文件')
  const body = new FormData()
  body.set('drawingId', uploadForm.drawingId)
  body.set('versionNo', uploadForm.versionNo)
  body.set('file', uploadForm.file)
  const version = await api.request<DrawingVersion>('/api/versions/upload', { method: 'POST', body })
  previewVersionId.value = version.id
  selectedReviewVersion.value = version.id
  await refreshAll()
}

async function runReview() {
  const task = await api.request<ReviewTask>('/api/review-tasks', { method: 'POST', body: JSON.stringify({ versionId: selectedReviewVersion.value }) })
  selectedTask.value = task.id
  previewVersionId.value = selectedReviewVersion.value
  tab.value = 'issues'
  await refreshAll()
  await waitForTask(task.id)
}

async function retryTask(task: ReviewTask) {
  const created = await api.request<ReviewTask>(`/api/review-tasks/${task.id}/retry`, { method: 'POST' })
  selectedTask.value = created.id
  previewVersionId.value = created.versionId
  await refreshAll()
  await waitForTask(created.id)
}

async function updateIssue(issue: ReviewIssue, status: string) {
  await api.request(`/api/issues/${issue.id}`, { method: 'PATCH', body: JSON.stringify({ status, note: '前端工作台状态流转' }) })
  await refreshAll()
}

async function createReport() {
  const report = await api.request<ReportDocument>('/api/reports', { method: 'POST', body: JSON.stringify({ taskId: selectedTask.value }) })
  reportDocument.value = report
  reportContent.value = report.content
  reportActionMessage.value = ''
  output.value = ''
  await refreshAll()
}

async function compareVersions() {
  const result = await api.request(`/api/versions/compare?leftId=${compare.leftId}&rightId=${compare.rightId}`)
  output.value = JSON.stringify(result, null, 2)
  reportActionMessage.value = ''
}

async function copyReport() {
  if (!reportContent.value) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(reportContent.value)
    } else if (!copyReportWithTextarea(reportContent.value)) {
      throw new Error('当前浏览器不允许写入剪贴板')
    }
    reportActionMessage.value = '报告 Markdown 已复制'
  } catch (reason) {
    if (copyReportWithTextarea(reportContent.value)) {
      reportActionMessage.value = '报告 Markdown 已复制'
      return
    }
    reportActionMessage.value = `复制失败：${messageOf(reason)}`
  }
}

function downloadReport() {
  if (!reportContent.value) return
  const name = `${reportDocument.value?.id ?? 'shipcad-review-report'}.md`
  const blob = new Blob([reportContent.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  URL.revokeObjectURL(url)
  reportActionMessage.value = `已下载 ${name}`
}

function copyReportWithTextarea(value: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  return copied
}

function selectIssue(issue: ReviewIssue) {
  selectedIssueId.value = issue.id
  previewVersionId.value = issue.versionId
}

function coordinateLabel(entity: ParsedEntity): string {
  if (entity.x == null || entity.y == null) return ''
  return ` / 坐标 ${entity.x.toFixed(1)}, ${entity.y.toFixed(1)}`
}

function issueEvidence(issue: ReviewIssue): string {
  const entity = issue.entityRef ? entityById.value.get(issue.entityRef) : undefined
  if (entity) {
    const text = entity.textValue ? ` / 内容 ${entity.textValue}` : ''
    return `图元定位：${entity.entityType} / 图层 ${entity.layerName || '-'}${coordinateLabel(entity)}${text}`
  }
  if (issue.entityRef) {
    return `图元定位：${issue.entityRef}（切换到对应版本后加载详细图元）`
  }
  if (issue.layerName) {
    return `图层定位：${issue.layerName}`
  }
  return '版本级问题：需要结合图纸版本或审查任务上下文确认'
}

function structuredEvidenceItems(issue: ReviewIssue): string[] {
  if (!issue.evidences?.length) return [issueEvidence(issue)]
  return issue.evidences.map(formatEvidence)
}

function formatEvidence(evidence: ReviewEvidence): string {
  const source = evidence.sourceId || evidence.sourceLabel || '-'
  const summary = evidence.summary || evidence.payloadJson || '-'
  return `${evidence.evidenceType} / ${source}: ${summary}`
}

function barRows(data: Record<string, number> = {}) {
  const max = Math.max(1, ...Object.values(data))
  return Object.entries(data).map(([key, value]) => ({ key, value, width: `${(value / max) * 100}%` }))
}

watch(previewVersionId, () => {
  refreshPreview(true).catch((reason) => {
    previewFileError.value = messageOf(reason)
  })
})

onBeforeUnmount(releasePreviewFileUrl)

onMounted(() => {
  if (api.token) {
    loginState.label = '已读取本地登录状态'
    refreshAll().catch(() => localStorage.removeItem('shipcad_token'))
  }
})
</script>

<template>
  <main class="shell">
    <aside class="sidebar">
      <div>
        <p class="eyebrow">ShipCAD Review</p>
        <h1>船舶CAD图纸AI智能审查平台</h1>
      </div>

      <form class="login panel" @submit.prevent="login">
        <label>账号<input v-model="loginState.username" /></label>
        <label>密码<input v-model="loginState.password" type="password" /></label>
        <button>登录</button>
        <span>{{ loginState.label }}</span>
      </form>

      <nav>
        <button :class="{ active: tab === 'dashboard' }" @click="tab = 'dashboard'">统计看板</button>
        <button :class="{ active: tab === 'projects' }" @click="tab = 'projects'">项目与图纸</button>
        <button :class="{ active: tab === 'issues' }" @click="tab = 'issues'">问题闭环</button>
        <button :class="{ active: tab === 'reports' }" @click="tab = 'reports'">报告与对比</button>
      </nav>
    </aside>

    <section class="content">
      <div class="topline">
        <strong>{{ loading ? '同步中...' : '商业化MVP工作台' }}</strong>
        <button @click="refreshAll">刷新</button>
      </div>

      <section v-if="tab === 'dashboard'">
        <div class="kpis">
          <div class="kpi"><span>项目</span><strong>{{ dashboard?.projectCount ?? 0 }}</strong></div>
          <div class="kpi"><span>图纸</span><strong>{{ dashboard?.drawingCount ?? 0 }}</strong></div>
          <div class="kpi"><span>版本</span><strong>{{ dashboard?.versionCount ?? 0 }}</strong></div>
          <div class="kpi"><span>任务</span><strong>{{ dashboard?.taskCount ?? 0 }}</strong></div>
          <div class="kpi"><span>未关闭问题</span><strong>{{ dashboard?.openIssueCount ?? 0 }}</strong></div>
        </div>
        <div class="grid two">
          <div class="panel">
            <h2>严重等级分布</h2>
            <div v-for="row in barRows(dashboard?.issueCountBySeverity)" :key="row.key" class="bar-row">
              <span>{{ row.key }}</span><div class="bar"><i :style="{ width: row.width }"></i></div><b>{{ row.value }}</b>
            </div>
          </div>
          <div class="panel">
            <h2>规则命中分布</h2>
            <div v-for="row in barRows(dashboard?.issueCountByRule)" :key="row.key" class="bar-row">
              <span>{{ row.key }}</span><div class="bar"><i :style="{ width: row.width }"></i></div><b>{{ row.value }}</b>
            </div>
          </div>
        </div>
      </section>

      <section v-if="tab === 'projects'">
        <div class="grid two">
          <form class="panel" @submit.prevent="createProject">
            <h2>创建项目</h2>
            <label>项目名称<input v-model="projectForm.name" /></label>
            <label>船号<input v-model="projectForm.shipNo" /></label>
            <label>业主<input v-model="projectForm.owner" /></label>
            <label>说明<textarea v-model="projectForm.description"></textarea></label>
            <button>创建项目</button>
          </form>
          <form class="panel" @submit.prevent="createDrawing">
            <h2>创建图纸</h2>
            <label>项目<select v-model="drawingForm.projectId"><option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option></select></label>
            <label>图号<input v-model="drawingForm.drawingNo" /></label>
            <label>图名<input v-model="drawingForm.title" /></label>
            <label>专业<input v-model="drawingForm.discipline" /></label>
            <button>创建图纸</button>
          </form>
        </div>
        <form class="panel inline" @submit.prevent="uploadVersion">
          <h2>上传CAD版本</h2>
          <select v-model="uploadForm.drawingId"><option v-for="d in drawings" :key="d.id" :value="d.id">{{ d.drawingNo }} {{ d.title }}</option></select>
          <input v-model="uploadForm.versionNo" />
          <input type="file" accept=".dxf,.dwg" @change="uploadForm.file = ($event.target as HTMLInputElement).files?.[0] ?? null" />
          <button>上传版本</button>
        </form>
        <div class="grid two">
          <div class="panel"><h2>项目</h2><div v-for="p in projects" :key="p.id" class="item"><strong>{{ p.name }}</strong><p>{{ p.shipNo }} / {{ p.owner }}</p></div></div>
          <div class="panel"><h2>图纸版本</h2><div v-for="v in selectedDrawingVersions" :key="v.id" class="item"><strong>{{ versionLabel(v) }}</strong><p>{{ v.fileName }} / {{ v.parseStatus }} / 实体 {{ parseSummary(v).entityCount ?? 0 }}</p></div></div>
        </div>
      </section>

      <section v-if="tab === 'issues'">
        <form class="panel inline" @submit.prevent="runReview">
          <h2>发起审查任务</h2>
          <select v-model="selectedReviewVersion"><option v-for="v in versions" :key="v.id" :value="v.id">{{ versionLabel(v) }}</option></select>
          <button>发起审查</button>
        </form>
        <div class="panel">
          <h2>审查任务队列</h2>
          <div v-for="task in tasks" :key="task.id" class="task-row">
            <div>
              <strong>{{ taskLabel(task) }}</strong>
              <p>版本：{{ taskVersionLabel(task) }}</p>
              <p v-if="task.errorMessage" class="error">{{ task.errorMessage }}</p>
            </div>
            <button v-if="task.status === 'FAILED'" @click="retryTask(task)">重试</button>
          </div>
        </div>
        <div class="preview-grid">
          <div class="panel preview-panel">
            <div class="section-title">
              <h2>DXF正式预览与问题定位</h2>
              <select v-model="previewVersionId"><option v-for="v in versions" :key="v.id" :value="v.id">{{ versionLabel(v) }}</option></select>
            </div>
            <div v-if="selectedIssue" class="location-summary">
              <strong>当前定位证据</strong>
              <span>{{ issueEvidence(selectedIssue) }}</span>
            </div>
            <DxfViewerPreview
              v-if="previewFileUrl && isDxfPreview"
              :file-url="previewFileUrl"
              :issues="previewIssues"
              :selected-issue-id="selectedIssueId"
              @load-failed="previewFileError = $event"
            />
            <div v-else class="preview-state">
              <strong>{{ previewFileError || '正在准备DXF正式预览文件' }}</strong>
              <p>正式链路不会自动切换到Canvas；若这里失败，请优先修复dxf-viewer、文件服务或DXF兼容性问题。</p>
            </div>
            <div class="preview-actions">
              <button type="button" class="secondary" @click="showCanvasDiagnostics = !showCanvasDiagnostics">
                {{ showCanvasDiagnostics ? '关闭Canvas诊断' : '打开Canvas诊断' }}
              </button>
            </div>
            <div v-if="showCanvasDiagnostics" class="canvas-diagnostics">
              <div class="diagnostic-title">
                <strong>Canvas诊断视图</strong>
                <span>仅用于比对解析实体，不作为正式预览兜底。</span>
              </div>
              <DxfCanvasDiagnostics :entities="entities" :issues="previewIssues" :selected-issue-id="selectedIssueId" />
            </div>
            <p class="hint">正式预览使用dxf-viewer；问题图层会在右侧图层列表中标记。Canvas只用于人工诊断解析实体。</p>
          </div>
          <div class="panel">
            <h2>问题清单</h2>
            <div v-for="issue in issues" :key="issue.id" class="issue" :class="[issue.severity, { selected: selectedIssueId === issue.id }]" @click="selectIssue(issue)">
              <strong>{{ issue.title }}</strong>
              <p>{{ issue.ruleCode }} / {{ issue.severity }} / {{ issue.status }} / 图层 {{ issue.layerName || '-' }}</p>
              <p class="evidence">{{ issueEvidence(issue) }}</p>
              <ul class="evidence-list">
                <li v-for="item in structuredEvidenceItems(issue)" :key="item">{{ item }}</li>
              </ul>
              <p>{{ issue.description }}</p>
              <p>建议：{{ issue.suggestion }}</p>
              <div class="actions">
                <button @click.stop="updateIssue(issue, 'IN_PROGRESS')">整改中</button>
                <button @click.stop="updateIssue(issue, 'READY_FOR_REVIEW')">待复核</button>
                <button @click.stop="updateIssue(issue, 'CLOSED')">关闭</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="tab === 'reports'">
        <div class="grid two">
          <form class="panel" @submit.prevent="createReport">
            <h2>审查报告</h2>
            <label>任务<select v-model="selectedTask"><option v-for="t in tasks" :key="t.id" :value="t.id">{{ taskLabel(t) }}</option></select></label>
            <button>生成报告</button>
          </form>
          <form class="panel" @submit.prevent="compareVersions">
            <h2>版本对比</h2>
            <label>旧版本<select v-model="compare.leftId"><option v-for="v in versions" :key="v.id" :value="v.id">{{ versionLabel(v) }}</option></select></label>
            <label>新版本<select v-model="compare.rightId"><option v-for="v in versions" :key="v.id" :value="v.id">{{ versionLabel(v) }}</option></select></label>
            <button>对比</button>
          </form>
        </div>

        <article v-if="reportContent" class="report-document">
          <header class="report-header">
            <div>
              <p class="eyebrow">Evidence-aware report</p>
              <h2>{{ reportTitle }}</h2>
              <span>{{ selectedReportTask ? taskLabel(selectedReportTask) : '未选择任务' }}</span>
            </div>
            <div class="report-actions">
              <button type="button" class="secondary" @click="copyReport">复制Markdown</button>
              <button type="button" @click="downloadReport">下载.md</button>
            </div>
          </header>

          <div class="report-metrics">
            <div><span>问题数</span><strong>{{ selectedReportIssues.length }}</strong></div>
            <div><span>高风险</span><strong>{{ reportHighIssueCount }}</strong></div>
            <div><span>实体证据</span><strong>{{ reportEntityEvidenceCount }}</strong></div>
            <div><span>图层证据</span><strong>{{ reportLayerEvidenceCount }}</strong></div>
            <div><span>结构化证据</span><strong>{{ reportStructuredEvidenceCount }}</strong></div>
            <div><span>解析实体</span><strong>{{ summaryNumber('entityCount') }}</strong></div>
          </div>
          <p v-if="reportActionMessage" class="hint">{{ reportActionMessage }}</p>

          <section v-for="section in reportBodySections" :key="section.title" class="report-section">
            <h3>{{ section.title }}</h3>
            <template v-for="(block, index) in section.blocks" :key="`${section.title}-${index}`">
              <p v-if="block.type === 'paragraph'" class="report-paragraph">{{ block.text }}</p>
              <h4 v-else-if="block.type === 'subheading'">{{ block.text }}</h4>
              <ul v-else-if="block.type === 'list'" class="report-list">
                <li v-for="item in block.items" :key="item">{{ item }}</li>
              </ul>
              <div v-else class="report-table-wrap">
                <table class="report-table">
                  <thead>
                    <tr><th v-for="header in block.headers" :key="header">{{ header }}</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </section>

          <details class="raw-report">
            <summary>查看原始 Markdown</summary>
            <pre>{{ reportContent }}</pre>
          </details>
        </article>
        <div v-else class="panel empty-report">
          <h2>尚未生成报告</h2>
          <p>选择一个已完成的审查任务后生成报告。系统会把规则、图层、实体引用和解析摘要整理成可追溯审查材料。</p>
        </div>

        <div v-if="output" class="panel">
          <h2>版本对比结果</h2>
          <pre>{{ output }}</pre>
        </div>
      </section>
    </section>
  </main>
</template>
