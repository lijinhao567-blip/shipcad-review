<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api, type AccessProfile, type AuditLogPage, type Dashboard, type Drawing, type DrawingVersion, type HealthComponent, type ManagedUser, type ParsedEntity, type Project, type ProjectMember, type RemediationRecord, type ReportDocument, type ReviewEvidence, type ReviewIssue, type ReviewTask, type ReviewTaskStep, type SystemHealth, type UserView, type VersionCompareResponse } from './api'

const DxfViewerPreview = defineAsyncComponent(() => import('./components/DxfViewerPreview.vue'))
const DxfCanvasDiagnostics = defineAsyncComponent(() => import('./components/DxfCanvas.vue'))

const tab = ref<'dashboard' | 'projects' | 'issues' | 'reports' | 'status' | 'audit' | 'users'>('dashboard')
const loginState = reactive({ username: 'admin', password: 'admin123', label: '未登录' })
const authenticated = ref(Boolean(api.token))
const currentUser = ref<UserView | null>(null)
const permissions = ref<string[]>([])
const sessionExpiresAt = ref('')
const projectForm = reactive({ name: 'A22船舶审图试点', shipNo: 'S-2026-001', owner: '设计院审图组', description: 'DXF优先的AI辅助审图试点项目' })
const drawingForm = reactive({ projectId: '', drawingNo: 'A22-SEC-001', title: '船体分段结构图', discipline: '船体结构' })
const uploadForm = reactive({ drawingId: '', versionNo: 'V1', file: null as File | null })
const reviewOptions = reactive({ autoVision: false, autoOcr: false, forceRender: false, visionConfidence: 0.25, ocrConfidence: 0.5 })
const selectedReviewVersion = ref('')
const selectedTask = ref('')
const selectedTaskDetailId = ref('')
const previewVersionId = ref('')
const previewFileUrl = ref('')
const previewFileError = ref('')
const selectedIssueId = ref('')
const showCanvasDiagnostics = ref(false)
const visionForm = reactive({ file: null as File | null, confidence: 0.25 })
const visionDetecting = ref(false)
const visionMessage = ref('')
const ocrForm = reactive({ file: null as File | null, confidence: 0.5 })
const ocrRecognizing = ref(false)
const ocrMessage = ref('')
const compare = reactive({ leftId: '', rightId: '' })
const compareResult = ref<VersionCompareResponse | null>(null)
const compareMessage = ref('')
const remediationForm = reactive({ status: 'IN_PROGRESS', assignee: '', note: '', reportId: '' })
const remediationRecords = ref<RemediationRecord[]>([])
const remediationMessage = ref('')
const remediationLoading = ref(false)
const reportDocument = ref<ReportDocument | null>(null)
const reportContent = ref('')
const reportActionMessage = ref('')
const systemHealth = ref<SystemHealth | null>(null)
const healthLoading = ref(false)
const healthMessage = ref('')
const auditFilters = reactive({ actor: '', action: '', targetType: '' })
const auditPage = ref<AuditLogPage | null>(null)
const auditLoading = ref(false)
const auditMessage = ref('')
const managedUsers = ref<ManagedUser[]>([])
const selectedManagedUserId = ref('')
const userCreateForm = reactive({ username: '', displayName: '', role: 'VIEWER', password: '', enabled: true })
const userEditForm = reactive({ displayName: '', role: 'VIEWER', enabled: true })
const resetPasswordForm = reactive({ newPassword: '' })
const changePasswordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const userManagementMessage = ref('')
const accountMessage = ref('')
const userManagementLoading = ref(false)
const projectMembers = ref<ProjectMember[]>([])
const selectedProjectMemberUserId = ref('')
const projectMemberMessage = ref('')
const projectMemberLoading = ref(false)
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

type HealthItem = {
  key: string
  name: string
  status: string
  required: boolean
  detail: string
  endpoint: string
}

type EvidenceGroup = {
  type: string
  label: string
  items: string[]
}

type WorkflowStep = {
  key: string
  label: string
  detail: string
  state: 'done' | 'current' | 'blocked'
  disabled: boolean
}

const dashboard = ref<Dashboard | null>(null)
const projects = ref<Project[]>([])
const drawings = ref<Drawing[]>([])
const versions = ref<DrawingVersion[]>([])
const tasks = ref<ReviewTask[]>([])
const issues = ref<ReviewIssue[]>([])
const entities = ref<ParsedEntity[]>([])
const versionEvidences = ref<ReviewEvidence[]>([])
const loading = ref(false)
const selectedManagedUser = computed(() => managedUsers.value.find((user) => user.id === selectedManagedUserId.value))
const availableProjectMemberUsers = computed(() => {
  const assigned = new Set(projectMembers.value.map((member) => member.userId))
  return managedUsers.value.filter((user) => user.enabled && !assigned.has(user.id))
})
const roleLabel = computed(() => {
  return currentUser.value ? roleName(currentUser.value.role) : '未登录'
})

function can(permission: string): boolean {
  return permissions.value.includes(permission)
}

function applyAccessProfile(profile: AccessProfile) {
  currentUser.value = profile.user
  permissions.value = profile.permissions ?? []
  sessionExpiresAt.value = profile.sessionExpiresAt ?? ''
  authenticated.value = true
  loginState.username = profile.user.username
  loginState.password = ''
  loginState.label = `已登录：${profile.user.displayName}`
}

function clearSession() {
  api.clearToken()
  authenticated.value = false
  currentUser.value = null
  permissions.value = []
  sessionExpiresAt.value = ''
  auditPage.value = null
  managedUsers.value = []
  projectMembers.value = []
  selectedProjectMemberUserId.value = ''
  loginState.label = '未登录'
}

function roleName(role: string): string {
  const labels: Record<string, string> = {
    ADMIN: '系统管理员',
    REVIEW_EXPERT: '审图专家',
    DESIGN_ENGINEER: '设计工程师',
    VIEWER: '只读访客'
  }
  return labels[role] ?? role
}

const selectedProject = computed(() => projects.value.find((item) => item.id === drawingForm.projectId) ?? projects.value[0])
const selectedProjectDrawings = computed(() => selectedProject.value ? drawings.value.filter((item) => item.projectId === selectedProject.value?.id) : drawings.value)
const selectedDrawing = computed(() => drawings.value.find((item) => item.id === uploadForm.drawingId) ?? selectedProjectDrawings.value[0])
const selectedDrawingVersions = computed(() => selectedDrawing.value ? versions.value.filter((item) => item.drawingId === selectedDrawing.value?.id) : versions.value)
const currentVersion = computed(() =>
  versions.value.find((item) => item.id === selectedReviewVersion.value)
  ?? versions.value.find((item) => item.id === previewVersionId.value)
  ?? selectedDrawingVersions.value[0]
  ?? versions.value[0]
)
const previewIssues = computed(() => issues.value.filter((issue) => !previewVersionId.value || issue.versionId === previewVersionId.value))
const previewVersion = computed(() => versions.value.find((item) => item.id === previewVersionId.value))
const isDxfPreview = computed(() => previewVersion.value?.fileName.toLowerCase().endsWith('.dxf') ?? false)
const entityById = computed(() => new Map(entities.value.map((entity) => [entity.id, entity])))
const selectedIssue = computed(() => issues.value.find((issue) => issue.id === selectedIssueId.value))
const selectedIssueRecords = computed(() => remediationRecords.value.filter((record) => record.issueId === selectedIssueId.value))
const selectedIssueReportRef = computed(() => {
  if (!selectedIssue.value || reportDocument.value?.taskId !== selectedIssue.value.taskId) return ''
  return reportDocument.value.id
})
const previewVisionEvidences = computed(() => versionEvidences.value.filter((evidence) => evidence.evidenceType === 'YOLO_SYMBOL'))
const previewOcrEvidences = computed(() => versionEvidences.value.filter((evidence) => evidence.evidenceType === 'OCR_TEXT'))
const selectedReportTask = computed(() => tasks.value.find((task) => task.id === selectedTask.value))
const selectedReportVersion = computed(() => versions.value.find((version) => version.id === selectedReportTask.value?.versionId))
const selectedReportIssues = computed(() => issues.value.filter((issue) => issue.taskId === selectedTask.value))
const selectedTaskDetail = computed(() => tasks.value.find((task) => task.id === selectedTaskDetailId.value))
const selectedTaskDetailIssues = computed(() => issues.value.filter((issue) => issue.taskId === selectedTaskDetailId.value))
const selectedTaskDetailOpenIssueCount = computed(() => selectedTaskDetailIssues.value.filter((issue) => issue.status !== 'CLOSED').length)
const selectedTaskDetailEvidenceCount = computed(() => selectedTaskDetailIssues.value.reduce((sum, issue) => sum + (issue.evidences?.length ?? 0), 0))
const reportCandidateTask = computed(() => {
  if (selectedReportTask.value?.status === 'FINISHED') return selectedReportTask.value
  return tasks.value.find((task) => task.status === 'FINISHED')
})
const systemHealthItems = computed(() => {
  const health = systemHealth.value
  if (!health) return []
  return [
    {
      key: 'backend',
      name: '后端 API',
      status: health.status || 'unknown',
      required: true,
      detail: `健康检查时间：${formatTime(health.time)}`,
      endpoint: '/api/health'
    },
    healthItem('database', '数据库', health.database, true),
    healthItem('queue', '审查任务队列', health.queue, true),
    healthItem('storage', '对象存储', health.storage, true),
    healthItem('openapi', 'OpenAPI 文档', health.openapi, true),
    healthItem('cad', 'CAD Worker', health.workers?.cad, true),
    healthItem('vision', 'Vision Worker', health.workers?.vision, false),
    healthItem('ocr', 'OCR Worker', health.workers?.ocr, false)
  ]
})
const workflowSteps = computed<WorkflowStep[]>(() => {
  const systemReady = Boolean(systemHealth.value)
  const projectReady = Boolean(selectedProject.value && selectedDrawing.value)
  const versionReady = Boolean(currentVersion.value)
  const reviewReady = Boolean(selectedTaskDetail.value)
  const taskComplete = selectedTaskDetail.value?.status === 'FINISHED'
  const issueReady = reviewReady && (taskComplete || selectedTaskDetail.value?.status === 'FAILED')
  const reportReady = Boolean(reportContent.value)
  const reportCanGenerate = Boolean(reportCandidateTask.value && can('REPORT_GENERATE'))
  return [
    {
      key: 'status',
      label: '系统',
      detail: systemHealth.value ? healthStatusLabel(systemHealth.value.status) : '待检查',
      state: systemReady ? 'done' : 'current',
      disabled: false
    },
    {
      key: 'login',
      label: '登录',
      detail: authenticated.value ? loginState.label.replace(/^已登录：/, '') : '未登录',
      state: authenticated.value ? 'done' : systemReady ? 'current' : 'blocked',
      disabled: false
    },
    {
      key: 'project',
      label: '项目图纸',
      detail: projectReady ? `${selectedProject.value?.name} / ${selectedDrawing.value?.drawingNo}` : '待创建',
      state: projectReady ? 'done' : authenticated.value ? 'current' : 'blocked',
      disabled: !authenticated.value
    },
    {
      key: 'version',
      label: '版本',
      detail: currentVersion.value ? `${currentVersion.value.versionNo} / ${currentVersion.value.parseStatus}` : '待上传',
      state: versionReady ? 'done' : projectReady ? 'current' : 'blocked',
      disabled: !projectReady
    },
    {
      key: 'review',
      label: '审查',
      detail: selectedTaskDetail.value ? `${taskStageLabel(selectedTaskDetail.value)} / ${selectedTaskDetail.value.issueCount}项` : '待发起',
      state: reviewReady ? 'done' : versionReady ? 'current' : 'blocked',
      disabled: !versionReady
    },
    {
      key: 'issues',
      label: '问题',
      detail: issueReady ? `${selectedTaskDetailIssues.value.length}项 / ${selectedTaskDetailOpenIssueCount.value}未关闭` : '待生成',
      state: issueReady ? 'done' : reviewReady ? 'current' : 'blocked',
      disabled: !reviewReady
    },
    {
      key: 'reports',
      label: '报告',
      detail: reportReady ? '已生成' : reportCanGenerate ? '可生成' : reportCandidateTask.value ? '待审图专家生成' : '待任务',
      state: reportReady ? 'done' : reportCanGenerate ? 'current' : 'blocked',
      disabled: !reportCandidateTask.value && !reportReady
    }
  ]
})
const reportSections = computed(() => parseReportSections(reportContent.value))
const reportTitle = computed(() => reportSections.value.find((section) => section.level === 1)?.title ?? '审查报告')
const reportBodySections = computed(() => reportSections.value.filter((section) => section.level === 2))
const reportSummary = computed(() => selectedReportVersion.value ? parseSummary(selectedReportVersion.value) : {})
const reportEntityEvidenceCount = computed(() => selectedReportIssues.value.filter((issue) => issue.entityRef).length)
const reportLayerEvidenceCount = computed(() => selectedReportIssues.value.filter((issue) => !issue.entityRef && issue.layerName).length)
const reportStructuredEvidenceCount = computed(() => selectedReportIssues.value.reduce((sum, issue) => sum + (issue.evidences?.length ?? 0), 0))
const reportHighIssueCount = computed(() => selectedReportIssues.value.filter((issue) => issue.severity === 'HIGH').length)
const compareTextChangeCount = computed(() => (compareResult.value?.addedTexts.length ?? 0) + (compareResult.value?.removedTexts.length ?? 0))
const compareLayerChangeCount = computed(() => (compareResult.value?.addedLayers.length ?? 0) + (compareResult.value?.removedLayers.length ?? 0))
const compareBlockChangeCount = computed(() => (compareResult.value?.addedBlocks.length ?? 0) + (compareResult.value?.removedBlocks.length ?? 0))
const compareRiskCount = computed(() => compareResult.value?.riskHints.length ?? 0)

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

function signedNumber(value: number): string {
  return value > 0 ? `+${value}` : `${value}`
}

function compareListText(items: string[]): string {
  return items.length ? items.join('、') : '无'
}

function healthItem(key: string, name: string, component: HealthComponent | undefined, required: boolean): HealthItem {
  const status = component?.status || 'unknown'
  return {
    key,
    name: component?.name || name,
    status,
    required: component?.required ?? required,
    detail: healthDetail(component),
    endpoint: component?.baseUrl || component?.url || '-'
  }
}

function healthDetail(component: HealthComponent | undefined): string {
  if (!component) return '尚未返回该组件状态'
  if (component.error) return `错误：${component.error}`
  const parts = []
  if (component.statusCode) parts.push(`health HTTP ${component.statusCode}`)
  if (component.capabilitiesStatusCode) parts.push(`capabilities HTTP ${component.capabilitiesStatusCode}`)
  const capabilities = compactValue(component.capabilities)
  const health = compactValue(component.health)
  if (capabilities) parts.push(`能力：${capabilities}`)
  else if (health) parts.push(`状态：${health}`)
  else {
    const componentSummary = compactValue(component)
    if (componentSummary) parts.push(`状态：${componentSummary}`)
  }
  return parts.join('；') || '状态已返回，暂无更多细节'
}

function compactValue(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'string') return shorten(value)
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return shorten(value.map((item) => compactValue(item)).filter(Boolean).join(', '))
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    const preferredKeys = ['status', 'mode', 'bucket', 'endpoint', 'root', 'cacheRoot', 'queuedCount', 'processingCount', 'localQueuedCount', 'activeCount', 'workerRunning', 'engine', 'engineAvailable', 'modelConfigured', 'modelPath', 'commandAvailable', 'formats', 'version']
    const preferred = preferredKeys
      .filter((key) => record[key] != null)
      .map((key) => `${key}=${compactValue(record[key])}`)
    if (preferred.length) return shorten(preferred.join('，'))
    return shorten(JSON.stringify(record))
  }
  return shorten(String(value))
}

function shorten(value: string, length = 160): string {
  return value.length > length ? `${value.slice(0, length)}...` : value
}

function healthStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    ok: '正常',
    degraded: '部分可用',
    down: '不可用',
    unknown: '未知'
  }
  return labels[status] ?? status
}

function healthStatusClass(status: string): string {
  const normalized = ['ok', 'degraded', 'down'].includes(status) ? status : 'unknown'
  return `health-card ${normalized}`
}

function formatTime(value?: string | null): string {
  if (!value) return '-'
  const time = new Date(value)
  return Number.isNaN(time.getTime()) ? value : time.toLocaleString()
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

function issueStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    OPEN: '待处理',
    IN_PROGRESS: '整改中',
    READY_FOR_REVIEW: '待复核',
    CLOSED: '已关闭'
  }
  return labels[status] ?? status
}

function remediationActionLabel(action: string): string {
  const labels: Record<string, string> = {
    START_REMEDIATION: '开始整改',
    SUBMIT_FOR_REVIEW: '提交复核',
    CLOSE: '关闭问题',
    REOPEN: '重新打开',
    MARK_OPEN: '标记待处理',
    ASSIGN: '指派经办人',
    COMMENT: '补充说明',
    UPDATE: '更新'
  }
  return labels[action] ?? action
}

function taskVersionLabel(task: ReviewTask): string {
  const version = versions.value.find((item) => item.id === task.versionId)
  return version ? versionLabel(version) : task.versionId
}

function taskAutomationLabel(task: ReviewTask): string {
  const modes = []
  if (task.autoVision) modes.push('视觉')
  if (task.autoOcr) modes.push('OCR')
  if (!modes.length) return '自动证据：未启用'
  const render = task.forceRender ? ' / 强制重渲染' : ''
  return `自动证据：${modes.join('+')}${render}`
}

function taskStageLabel(task: ReviewTask): string {
  const labels: Record<string, string> = {
    QUEUED: '排队中',
    PARSING: 'CAD解析',
    RENDERING: '生成渲染图',
    VISION_DETECTING: '视觉识别',
    OCR_RECOGNIZING: 'OCR识别',
    RULE_REVIEWING: '规则审查',
    FINISHED: '已完成',
    FAILED: '失败'
  }
  const stage = task.stage || task.status
  return labels[stage] ?? stage
}

function stepStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    RUNNING: '进行中',
    SUCCESS: '成功',
    SKIPPED: '跳过',
    FAILED: '失败'
  }
  return labels[status] ?? status
}

function taskStepClass(step: ReviewTaskStep): string {
  return `task-step ${step.status.toLowerCase()}`
}

function workflowStepClass(step: WorkflowStep): string {
  return `workflow-step ${step.state}`
}

function activateWorkflowStep(key: string) {
  if (key === 'status') {
    tab.value = 'status'
    return
  }
  if (key === 'login') {
    tab.value = 'dashboard'
    return
  }
  if (key === 'project' || key === 'version') {
    tab.value = 'projects'
    return
  }
  if (key === 'review' || key === 'issues') {
    if (currentVersion.value) {
      selectedReviewVersion.value = currentVersion.value.id
      previewVersionId.value = currentVersion.value.id
    }
    tab.value = 'issues'
    return
  }
  if (key === 'reports') {
    if (reportCandidateTask.value) selectedTask.value = reportCandidateTask.value.id
    tab.value = 'reports'
  }
}

function selectProject(project: Project) {
  drawingForm.projectId = project.id
  const drawing = drawings.value.find((item) => item.projectId === project.id)
  if (drawing) selectDrawing(drawing)
}

function selectDrawing(drawing: Drawing) {
  uploadForm.drawingId = drawing.id
  drawingForm.projectId = drawing.projectId
  const version = versions.value.find((item) => item.drawingId === drawing.id)
  if (version) selectVersion(version)
}

function selectVersion(version: DrawingVersion) {
  uploadForm.drawingId = version.drawingId
  selectedReviewVersion.value = version.id
  previewVersionId.value = version.id
  if (!compare.leftId) compare.leftId = version.id
}

function shortId(value?: string): string {
  if (!value) return '-'
  return value.length > 12 ? value.slice(0, 12) : value
}

async function refreshAll() {
  await refreshSystemHealth()
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
    if (!drawingForm.projectId || !p.some((project) => project.id === drawingForm.projectId)) {
      drawingForm.projectId = p[0]?.id ?? ''
    }
    const availableDrawings = dr.filter((drawing) => !drawingForm.projectId || drawing.projectId === drawingForm.projectId)
    if (!uploadForm.drawingId || !dr.some((drawing) => drawing.id === uploadForm.drawingId)) {
      uploadForm.drawingId = availableDrawings[0]?.id ?? dr[0]?.id ?? ''
    }
    const availableVersions = v.filter((version) => !uploadForm.drawingId || version.drawingId === uploadForm.drawingId)
    if (!selectedReviewVersion.value || !v.some((version) => version.id === selectedReviewVersion.value)) {
      selectedReviewVersion.value = availableVersions[0]?.id ?? v[0]?.id ?? ''
    }
    if (!previewVersionId.value || !v.some((version) => version.id === previewVersionId.value)) {
      previewVersionId.value = selectedReviewVersion.value || availableVersions[0]?.id || v[0]?.id || ''
    }
    if (!selectedTask.value || !t.some((task) => task.id === selectedTask.value)) {
      selectedTask.value = t[0]?.id ?? ''
    }
    if (!selectedTaskDetailId.value || !t.some((task) => task.id === selectedTaskDetailId.value)) {
      selectedTaskDetailId.value = selectedTask.value || t[0]?.id || ''
    }
    const comparableVersions = availableVersions.length ? availableVersions : v
    if (!compare.leftId || !comparableVersions.some((version) => version.id === compare.leftId)) {
      compare.leftId = comparableVersions[0]?.id ?? ''
    }
    if (!compare.rightId || !comparableVersions.some((version) => version.id === compare.rightId)) {
      compare.rightId = comparableVersions[1]?.id ?? comparableVersions[0]?.id ?? ''
    }
    if (compare.leftId === compare.rightId && comparableVersions.length > 1) {
      compare.rightId = comparableVersions.find((version) => version.id !== compare.leftId)?.id ?? compare.rightId
    }
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

async function refreshVersionEvidences() {
  if (!previewVersionId.value || !api.token) {
    versionEvidences.value = []
    return
  }
  const [vision, ocr] = await Promise.all([
    api.request<ReviewEvidence[]>(`/api/versions/${previewVersionId.value}/evidences?type=YOLO_SYMBOL`),
    api.request<ReviewEvidence[]>(`/api/versions/${previewVersionId.value}/evidences?type=OCR_TEXT`)
  ])
  versionEvidences.value = [...vision, ...ocr]
}

function messageOf(value: unknown): string {
  return value instanceof Error ? value.message : String(value)
}

async function refreshSystemHealth() {
  healthLoading.value = true
  healthMessage.value = ''
  try {
    systemHealth.value = await api.request<SystemHealth>('/api/health')
  } catch (reason) {
    healthMessage.value = `系统状态获取失败：${messageOf(reason)}`
  } finally {
    healthLoading.value = false
  }
}

async function refreshAudit(page = auditPage.value?.page ?? 0) {
  if (!can('AUDIT_VIEW')) {
    auditPage.value = null
    return
  }
  const params = new URLSearchParams({
    page: String(Math.max(0, page)),
    size: '50'
  })
  if (auditFilters.actor.trim()) params.set('actor', auditFilters.actor.trim())
  if (auditFilters.action.trim()) params.set('action', auditFilters.action.trim())
  if (auditFilters.targetType.trim()) params.set('targetType', auditFilters.targetType.trim())
  auditLoading.value = true
  auditMessage.value = ''
  try {
    auditPage.value = await api.request<AuditLogPage>(`/api/audit-logs?${params.toString()}`)
  } catch (reason) {
    auditMessage.value = `审计日志获取失败：${messageOf(reason)}`
  } finally {
    auditLoading.value = false
  }
}

async function refreshManagedUsers() {
  if (!can('USER_MANAGE')) {
    managedUsers.value = []
    return
  }
  userManagementLoading.value = true
  userManagementMessage.value = ''
  try {
    managedUsers.value = await api.request<ManagedUser[]>('/api/users')
    if (!selectedManagedUserId.value || !managedUsers.value.some((user) => user.id === selectedManagedUserId.value)) {
      selectedManagedUserId.value = managedUsers.value[0]?.id ?? ''
    }
    const selected = managedUsers.value.find((user) => user.id === selectedManagedUserId.value)
    if (selected) fillManagedUserForm(selected)
  } catch (reason) {
    userManagementMessage.value = `用户列表获取失败：${messageOf(reason)}`
  } finally {
    userManagementLoading.value = false
  }
}

async function refreshProjectMembers() {
  if (!can('PROJECT_MEMBER_MANAGE') || !selectedProject.value) {
    projectMembers.value = []
    selectedProjectMemberUserId.value = ''
    return
  }
  projectMemberLoading.value = true
  projectMemberMessage.value = ''
  try {
    projectMembers.value = await api.request<ProjectMember[]>(`/api/projects/${selectedProject.value.id}/members`)
    if (!availableProjectMemberUsers.value.some((user) => user.id === selectedProjectMemberUserId.value)) {
      selectedProjectMemberUserId.value = availableProjectMemberUsers.value[0]?.id ?? ''
    }
  } catch (reason) {
    projectMemberMessage.value = `项目成员获取失败：${messageOf(reason)}`
  } finally {
    projectMemberLoading.value = false
  }
}

async function addProjectMember() {
  if (!selectedProject.value || !selectedProjectMemberUserId.value) return
  projectMemberLoading.value = true
  projectMemberMessage.value = ''
  try {
    const added = await api.request<ProjectMember>(`/api/projects/${selectedProject.value.id}/members`, {
      method: 'POST',
      body: JSON.stringify({ userId: selectedProjectMemberUserId.value })
    })
    await refreshProjectMembers()
    projectMemberMessage.value = `已将 ${added.displayName} 加入当前项目`
  } catch (reason) {
    projectMemberMessage.value = `添加项目成员失败：${messageOf(reason)}`
  } finally {
    projectMemberLoading.value = false
  }
}

async function removeProjectMember(member: ProjectMember) {
  if (!selectedProject.value) return
  projectMemberLoading.value = true
  projectMemberMessage.value = ''
  try {
    await api.request(`/api/projects/${selectedProject.value.id}/members/${member.userId}`, {
      method: 'DELETE'
    })
    await refreshProjectMembers()
    projectMemberMessage.value = `已移除项目成员：${member.displayName}`
  } catch (reason) {
    projectMemberMessage.value = `移除项目成员失败：${messageOf(reason)}`
  } finally {
    projectMemberLoading.value = false
  }
}

function fillManagedUserForm(user: ManagedUser) {
  selectedManagedUserId.value = user.id
  userEditForm.displayName = user.displayName
  userEditForm.role = user.role
  userEditForm.enabled = user.enabled
  resetPasswordForm.newPassword = ''
  userManagementMessage.value = ''
}

async function createManagedUser() {
  userManagementLoading.value = true
  userManagementMessage.value = ''
  try {
    const created = await api.request<ManagedUser>('/api/users', {
      method: 'POST',
      body: JSON.stringify(userCreateForm)
    })
    userCreateForm.username = ''
    userCreateForm.displayName = ''
    userCreateForm.password = ''
    userCreateForm.role = 'VIEWER'
    userCreateForm.enabled = true
    await refreshManagedUsers()
    fillManagedUserForm(created)
    userManagementMessage.value = `已创建用户：${created.username}`
  } catch (reason) {
    userManagementMessage.value = `创建用户失败：${messageOf(reason)}`
  } finally {
    userManagementLoading.value = false
  }
}

async function updateManagedUser() {
  if (!selectedManagedUser.value) return
  userManagementLoading.value = true
  userManagementMessage.value = ''
  try {
    const updated = await api.request<ManagedUser>(`/api/users/${selectedManagedUser.value.id}`, {
      method: 'PATCH',
      body: JSON.stringify(userEditForm)
    })
    await refreshManagedUsers()
    fillManagedUserForm(updated)
    userManagementMessage.value = `已更新用户：${updated.username}`
  } catch (reason) {
    userManagementMessage.value = `更新用户失败：${messageOf(reason)}`
  } finally {
    userManagementLoading.value = false
  }
}

async function resetManagedPassword() {
  if (!selectedManagedUser.value) return
  userManagementLoading.value = true
  userManagementMessage.value = ''
  try {
    await api.request(`/api/users/${selectedManagedUser.value.id}/reset-password`, {
      method: 'POST',
      body: JSON.stringify({ newPassword: resetPasswordForm.newPassword })
    })
    resetPasswordForm.newPassword = ''
    await refreshManagedUsers()
    userManagementMessage.value = `已重置 ${selectedManagedUser.value.username} 的密码，并撤销其现有会话`
  } catch (reason) {
    userManagementMessage.value = `重置密码失败：${messageOf(reason)}`
  } finally {
    userManagementLoading.value = false
  }
}

async function changeOwnPassword() {
  accountMessage.value = ''
  if (changePasswordForm.newPassword !== changePasswordForm.confirmPassword) {
    accountMessage.value = '两次输入的新密码不一致'
    return
  }
  try {
    await api.request('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({
        currentPassword: changePasswordForm.currentPassword,
        newPassword: changePasswordForm.newPassword
      })
    })
    changePasswordForm.currentPassword = ''
    changePasswordForm.newPassword = ''
    changePasswordForm.confirmPassword = ''
    clearSession()
    loginState.label = '密码已修改，请使用新密码重新登录'
    accountMessage.value = '密码已修改，所有会话已撤销，请使用新密码重新登录'
    tab.value = 'dashboard'
  } catch (reason) {
    accountMessage.value = `修改密码失败：${messageOf(reason)}`
  }
}

function auditDetail(detailJson: string): string {
  if (!detailJson) return '-'
  try {
    return JSON.stringify(JSON.parse(detailJson))
  } catch {
    return detailJson
  }
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
  await Promise.all([refreshEntities(), refreshPreviewFile(resetDiagnostics), refreshVersionEvidences()])
}

async function login() {
  try {
    const result = await api.login(loginState.username, loginState.password)
    applyAccessProfile({ user: result.user, permissions: result.permissions, sessionExpiresAt: result.expiresAt })
    await refreshAll()
  } catch (reason) {
    clearSession()
    loginState.label = `登录失败：${messageOf(reason)}`
  }
}

async function logout() {
  try {
    await api.request('/api/auth/logout', { method: 'POST' })
  } catch {
    // Local cleanup still applies when the session is already expired.
  }
  clearSession()
  dashboard.value = null
  projects.value = []
  drawings.value = []
  versions.value = []
  tasks.value = []
  issues.value = []
  entities.value = []
  versionEvidences.value = []
  releasePreviewFileUrl()
  tab.value = 'dashboard'
}

async function createProject() {
  const project = await api.request<Project>('/api/projects', { method: 'POST', body: JSON.stringify(projectForm) })
  drawingForm.projectId = project.id
  await refreshAll()
}

async function createDrawing() {
  const drawing = await api.request<Drawing>('/api/drawings', { method: 'POST', body: JSON.stringify(drawingForm) })
  uploadForm.drawingId = drawing.id
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
  compare.leftId ||= version.id
  await refreshAll()
}

async function runReview() {
  const task = await api.request<ReviewTask>('/api/review-tasks', {
    method: 'POST',
    body: JSON.stringify({
      versionId: selectedReviewVersion.value,
      autoVision: reviewOptions.autoVision,
      autoOcr: reviewOptions.autoOcr,
      forceRender: reviewOptions.forceRender,
      visionConfidence: Number(reviewOptions.visionConfidence),
      ocrConfidence: Number(reviewOptions.ocrConfidence)
    })
  })
  selectedTask.value = task.id
  selectedTaskDetailId.value = task.id
  previewVersionId.value = selectedReviewVersion.value
  tab.value = 'issues'
  await refreshAll()
  await waitForTask(task.id)
}

async function runVisionDetection() {
  if (!previewVersionId.value) {
    visionMessage.value = '请选择图纸版本'
    return
  }
  if (!visionForm.file) {
    visionMessage.value = '请选择PNG或JPG图片'
    return
  }
  const confidence = Number(visionForm.confidence)
  if (!Number.isFinite(confidence) || confidence <= 0 || confidence > 1) {
    visionMessage.value = '置信度必须在0到1之间'
    return
  }
  const body = new FormData()
  body.set('file', visionForm.file)
  visionDetecting.value = true
  visionMessage.value = ''
  try {
    const generated = await api.request<ReviewEvidence[]>(`/api/versions/${previewVersionId.value}/vision-detect?confidence=${confidence}`, { method: 'POST', body })
    await refreshVersionEvidences()
    visionMessage.value = `视觉检测完成：生成 ${generated.length} 条 YOLO_SYMBOL 证据`
  } catch (reason) {
    visionMessage.value = `视觉检测失败：${messageOf(reason)}`
  } finally {
    visionDetecting.value = false
  }
}

async function runVisionDetectionRendered() {
  if (!previewVersionId.value) {
    visionMessage.value = '请选择图纸版本'
    return
  }
  const confidence = Number(visionForm.confidence)
  if (!Number.isFinite(confidence) || confidence <= 0 || confidence > 1) {
    visionMessage.value = '置信度必须在0到1之间'
    return
  }
  visionDetecting.value = true
  visionMessage.value = ''
  try {
    const generated = await api.request<ReviewEvidence[]>(`/api/versions/${previewVersionId.value}/vision-detect-rendered?confidence=${confidence}`, { method: 'POST' })
    await refreshVersionEvidences()
    visionMessage.value = `基于版本渲染图的视觉检测完成：生成 ${generated.length} 条 YOLO_SYMBOL 证据`
  } catch (reason) {
    visionMessage.value = `基于版本渲染图的视觉检测失败：${messageOf(reason)}`
  } finally {
    visionDetecting.value = false
  }
}

async function runOcrRecognition() {
  if (!previewVersionId.value) {
    ocrMessage.value = '请选择图纸版本'
    return
  }
  if (!ocrForm.file) {
    ocrMessage.value = '请选择PNG或JPG图片'
    return
  }
  const confidence = Number(ocrForm.confidence)
  if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1) {
    ocrMessage.value = '置信度必须在0到1之间'
    return
  }
  const body = new FormData()
  body.set('file', ocrForm.file)
  ocrRecognizing.value = true
  ocrMessage.value = ''
  try {
    const generated = await api.request<ReviewEvidence[]>(`/api/versions/${previewVersionId.value}/ocr-recognize?confidence=${confidence}`, { method: 'POST', body })
    await refreshVersionEvidences()
    ocrMessage.value = `OCR识别完成：生成 ${generated.length} 条 OCR_TEXT 证据`
  } catch (reason) {
    ocrMessage.value = `OCR识别失败：${messageOf(reason)}`
  } finally {
    ocrRecognizing.value = false
  }
}

async function runOcrRecognitionRendered() {
  if (!previewVersionId.value) {
    ocrMessage.value = '请选择图纸版本'
    return
  }
  const confidence = Number(ocrForm.confidence)
  if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1) {
    ocrMessage.value = '置信度必须在0到1之间'
    return
  }
  ocrRecognizing.value = true
  ocrMessage.value = ''
  try {
    const generated = await api.request<ReviewEvidence[]>(`/api/versions/${previewVersionId.value}/ocr-recognize-rendered?confidence=${confidence}`, { method: 'POST' })
    await refreshVersionEvidences()
    ocrMessage.value = `基于版本渲染图的OCR识别完成：生成 ${generated.length} 条 OCR_TEXT 证据`
  } catch (reason) {
    ocrMessage.value = `基于版本渲染图的OCR识别失败：${messageOf(reason)}`
  } finally {
    ocrRecognizing.value = false
  }
}

async function retryTask(task: ReviewTask) {
  const created = await api.request<ReviewTask>(`/api/review-tasks/${task.id}/retry`, { method: 'POST' })
  selectedTask.value = created.id
  selectedTaskDetailId.value = created.id
  previewVersionId.value = created.versionId
  await refreshAll()
  await waitForTask(created.id)
}

async function refreshIssueRemediations(issueId = selectedIssueId.value) {
  if (!issueId) {
    remediationRecords.value = []
    return
  }
  remediationRecords.value = await api.request<RemediationRecord[]>(`/api/issues/${issueId}/remediations`)
}

async function updateIssue(issue: ReviewIssue, status: string, note?: string) {
  await api.request(`/api/issues/${issue.id}`, {
    method: 'PATCH',
    body: JSON.stringify({
      status,
      assignee: remediationForm.assignee || issue.assignee || '',
      note: note ?? remediationForm.note,
      reportId: selectedIssueReportRef.value || remediationForm.reportId || ''
    })
  })
  await refreshAll()
  await refreshIssueRemediations(issue.id)
}

async function applyIssueWorkflow(status: string) {
  if (!selectedIssue.value) {
    remediationMessage.value = '请先选择一个问题'
    return
  }
  remediationLoading.value = true
  remediationMessage.value = ''
  try {
    await updateIssue(selectedIssue.value, status)
    remediationForm.status = status
    remediationForm.note = ''
    remediationMessage.value = `已记录：${issueStatusLabel(status)}`
  } catch (reason) {
    remediationMessage.value = `整改记录失败：${messageOf(reason)}`
  } finally {
    remediationLoading.value = false
  }
}

async function createReport() {
  const report = await api.request<ReportDocument>('/api/reports', { method: 'POST', body: JSON.stringify({ taskId: selectedTask.value }) })
  reportDocument.value = report
  reportContent.value = report.content
  reportActionMessage.value = ''
  tab.value = 'reports'
  await refreshAll()
}

async function compareVersions() {
  compareMessage.value = ''
  if (!compare.leftId || !compare.rightId) {
    compareMessage.value = '请选择两个版本'
    return
  }
  try {
    compareResult.value = await api.request<VersionCompareResponse>(`/api/versions/compare?leftId=${compare.leftId}&rightId=${compare.rightId}`)
    compareMessage.value = '版本对比完成'
    reportActionMessage.value = ''
  } catch (reason) {
    compareMessage.value = `版本对比失败：${messageOf(reason)}`
  }
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

async function downloadReport() {
  if (!reportDocument.value?.id) {
    reportActionMessage.value = '请先生成报告'
    return
  }
  try {
    const result = await api.download(`/api/reports/${reportDocument.value.id}/download`)
    const name = result.fileName ?? `${reportDocument.value.id}.md`
    saveBlob(result.blob, name)
    reportActionMessage.value = `已从服务端下载 ${name}`
  } catch (reason) {
    reportActionMessage.value = `下载失败：${messageOf(reason)}`
  }
}

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  URL.revokeObjectURL(url)
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
  remediationForm.status = issue.status === 'CLOSED' ? 'OPEN' : issue.status || 'IN_PROGRESS'
  remediationForm.assignee = issue.assignee || ''
  remediationForm.note = ''
  remediationForm.reportId = selectedIssueReportRef.value
  remediationMessage.value = ''
  refreshIssueRemediations(issue.id).catch((reason) => {
    remediationMessage.value = `整改时间线加载失败：${messageOf(reason)}`
  })
}

function selectTaskDetail(task: ReviewTask) {
  selectedTaskDetailId.value = task.id
  selectedTask.value = task.id
  previewVersionId.value = task.versionId
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

function groupedEvidence(issue: ReviewIssue): EvidenceGroup[] {
  if (!issue.evidences?.length) {
    return [{ type: 'LOCATION', label: '定位证据', items: [issueEvidence(issue)] }]
  }
  const order = ['CAD_ENTITY', 'CAD_LAYER', 'CAD_SUMMARY', 'RULE_RESULT', 'KNOWLEDGE_CLAUSE', 'YOLO_SYMBOL', 'OCR_TEXT']
  const groups = new Map<string, string[]>()
  for (const evidence of issue.evidences) {
    const type = evidence.evidenceType || 'UNKNOWN'
    const items = groups.get(type) ?? []
    items.push(formatEvidence(evidence))
    groups.set(type, items)
  }
  return [...groups.entries()]
    .sort(([left], [right]) => orderIndex(order, left) - orderIndex(order, right))
    .map(([type, items]) => ({ type, label: evidenceTypeLabel(type), items }))
}

function orderIndex(order: string[], value: string): number {
  const index = order.indexOf(value)
  return index === -1 ? order.length : index
}

function taskStepDetail(step: ReviewTaskStep): string {
  if (!step.detailJson) return ''
  try {
    return compactValue(JSON.parse(step.detailJson))
  } catch {
    return shorten(step.detailJson)
  }
}

function formatEvidence(evidence: ReviewEvidence): string {
  const source = evidence.sourceId || evidence.sourceLabel || '-'
  const sourceEvidenceId = sourceEvidenceIdOf(evidence)
  const summary = evidence.summary || evidence.payloadJson || '-'
  if (sourceEvidenceId) return `${evidenceTypeLabel(evidence.evidenceType)} / ${source} / sourceEvidenceId=${sourceEvidenceId}: ${summary}`
  return `${evidenceTypeLabel(evidence.evidenceType)} / ${source}: ${summary}`
}

function sourceEvidenceIdOf(evidence: ReviewEvidence): string {
  if (!evidence.payloadJson) return ''
  try {
    const payload = JSON.parse(evidence.payloadJson) as { sourceEvidenceId?: unknown }
    return typeof payload.sourceEvidenceId === 'string' ? payload.sourceEvidenceId : ''
  } catch {
    return ''
  }
}

function evidenceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    RULE_RESULT: '规则命中',
    CAD_ENTITY: 'CAD图元',
    CAD_LAYER: 'CAD图层',
    CAD_SUMMARY: '解析摘要',
    KNOWLEDGE_CLAUSE: '依据条款',
    YOLO_SYMBOL: '视觉符号',
    OCR_TEXT: 'OCR文字'
  }
  return labels[type] ?? type
}

function barRows(data: Record<string, number> = {}) {
  const max = Math.max(1, ...Object.values(data))
  return Object.entries(data).map(([key, value]) => ({ key, value, width: `${(value / max) * 100}%` }))
}

watch(previewVersionId, () => {
  visionMessage.value = ''
  ocrMessage.value = ''
  refreshPreview(true).catch((reason) => {
    previewFileError.value = messageOf(reason)
  })
})

watch(tab, (nextTab) => {
  if (nextTab === 'audit') {
    refreshAudit(0).catch(() => undefined)
  }
  if (nextTab === 'users') {
    refreshManagedUsers().catch(() => undefined)
  }
  if (nextTab === 'projects' && can('PROJECT_MEMBER_MANAGE')) {
    refreshManagedUsers().then(refreshProjectMembers).catch(() => undefined)
  }
})

watch(() => selectedProject.value?.id, () => {
  if (tab.value === 'projects' && can('PROJECT_MEMBER_MANAGE')) {
    refreshProjectMembers().catch(() => undefined)
  }
})

onBeforeUnmount(releasePreviewFileUrl)

onMounted(() => {
  refreshSystemHealth().catch(() => undefined)
  if (api.token) {
    api.request<AccessProfile>('/api/auth/me')
      .then((profile) => {
        applyAccessProfile(profile)
        return refreshAll()
      })
      .catch(() => clearSession())
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
        <div class="login-actions">
          <button>登录</button>
          <button v-if="authenticated" type="button" class="secondary" @click="logout">退出</button>
        </div>
        <span>{{ loginState.label }}</span>
        <span v-if="currentUser">{{ roleLabel }} / {{ currentUser.username }}</span>
      </form>

      <nav>
        <button :class="{ active: tab === 'dashboard' }" @click="tab = 'dashboard'">统计看板</button>
        <button :class="{ active: tab === 'projects' }" @click="tab = 'projects'">项目与图纸</button>
        <button :class="{ active: tab === 'issues' }" @click="tab = 'issues'">问题闭环</button>
        <button :class="{ active: tab === 'reports' }" @click="tab = 'reports'">报告与对比</button>
        <button v-if="authenticated" :class="{ active: tab === 'users' }" @click="tab = 'users'">账号与用户</button>
        <button v-if="can('AUDIT_VIEW')" :class="{ active: tab === 'audit' }" @click="tab = 'audit'">审计日志</button>
        <button :class="{ active: tab === 'status' }" @click="tab = 'status'">系统状态</button>
      </nav>
    </aside>

    <section class="content">
      <div class="topline">
        <div>
          <strong>{{ loading ? '同步中...' : '商业化MVP工作台' }}</strong>
          <span class="role-badge">{{ roleLabel }}</span>
        </div>
        <button @click="refreshAll">刷新</button>
      </div>

      <div class="workflow-panel">
        <div class="workflow-head">
          <div>
            <p class="eyebrow">Review flow</p>
            <h2>审图流程</h2>
          </div>
          <div class="context-strip">
            <span>项目 {{ shortId(selectedProject?.id) }}</span>
            <span>图纸 {{ selectedDrawing?.drawingNo ?? '-' }}</span>
            <span>版本 {{ currentVersion?.versionNo ?? '-' }}</span>
            <span>任务 {{ selectedTaskDetail ? taskStageLabel(selectedTaskDetail) : '-' }}</span>
          </div>
        </div>
        <div class="workflow-steps">
          <button
            v-for="(step, index) in workflowSteps"
            :key="step.key"
            type="button"
            :class="workflowStepClass(step)"
            :disabled="step.disabled"
            @click="activateWorkflowStep(step.key)"
          >
            <span>{{ index + 1 }}</span>
            <strong>{{ step.label }}</strong>
            <small>{{ step.detail }}</small>
          </button>
        </div>
      </div>

      <section v-if="tab === 'status'">
        <div class="panel">
          <div class="section-title">
            <h2>系统状态</h2>
            <button type="button" :disabled="healthLoading" @click="refreshSystemHealth">{{ healthLoading ? '检查中' : '重新检查' }}</button>
          </div>
          <p class="hint">这里用于确认后端、数据库、审查任务队列、对象存储、OpenAPI 和 Worker 的真实连通性。Vision/OCR 是可选能力，未启动时会显示不可用，但不会阻断核心 CAD 审查链路。</p>
          <p v-if="healthMessage" class="error">{{ healthMessage }}</p>
          <div v-if="systemHealthItems.length" class="health-grid">
            <div v-for="item in systemHealthItems" :key="item.key" :class="healthStatusClass(item.status)">
              <div class="health-card-head">
                <strong>{{ item.name }}</strong>
                <span>{{ item.required ? '必需' : '可选' }}</span>
              </div>
              <b>{{ healthStatusLabel(item.status) }}</b>
              <p>{{ item.detail }}</p>
              <small>{{ item.endpoint }}</small>
            </div>
          </div>
          <div v-else class="empty-state">
            <strong>尚未获取系统状态</strong>
            <p>点击重新检查，确认开发链路是否已启动。</p>
          </div>
        </div>
      </section>

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

      <section v-if="tab === 'users' && authenticated">
        <div class="grid two">
          <div class="panel account-summary">
            <h2>当前账号</h2>
            <dl>
              <div><dt>用户名</dt><dd>{{ currentUser?.username }}</dd></div>
              <div><dt>显示名称</dt><dd>{{ currentUser?.displayName }}</dd></div>
              <div><dt>角色</dt><dd>{{ roleLabel }}</dd></div>
              <div><dt>会话到期</dt><dd>{{ formatTime(sessionExpiresAt) }}</dd></div>
            </dl>
            <p class="hint">注销、修改密码、管理员重置密码、停用账号或调整角色后，相关会话会立即失效。</p>
          </div>
          <form class="panel" @submit.prevent="changeOwnPassword">
            <h2>修改密码</h2>
            <label>当前密码<input v-model="changePasswordForm.currentPassword" type="password" autocomplete="current-password" /></label>
            <label>新密码<input v-model="changePasswordForm.newPassword" type="password" autocomplete="new-password" /></label>
            <label>确认新密码<input v-model="changePasswordForm.confirmPassword" type="password" autocomplete="new-password" /></label>
            <button>修改密码并退出所有会话</button>
            <p class="hint">新密码至少10个字符，并同时包含字母和数字。</p>
            <p v-if="accountMessage" class="hint">{{ accountMessage }}</p>
          </form>
        </div>

        <template v-if="can('USER_MANAGE')">
          <div class="grid two">
            <form class="panel" @submit.prevent="createManagedUser">
              <h2>创建用户</h2>
              <label>用户名<input v-model="userCreateForm.username" autocomplete="off" placeholder="3-50位字母、数字或._-" /></label>
              <label>显示名称<input v-model="userCreateForm.displayName" /></label>
              <label>角色
                <select v-model="userCreateForm.role">
                  <option value="ADMIN">系统管理员</option>
                  <option value="REVIEW_EXPERT">审图专家</option>
                  <option value="DESIGN_ENGINEER">设计工程师</option>
                  <option value="VIEWER">只读访客</option>
                </select>
              </label>
              <label>初始密码<input v-model="userCreateForm.password" type="password" autocomplete="new-password" /></label>
              <label class="check-row"><input v-model="userCreateForm.enabled" type="checkbox" /> 创建后启用</label>
              <button :disabled="userManagementLoading">创建用户</button>
            </form>

            <div class="panel">
              <h2>编辑用户</h2>
              <template v-if="selectedManagedUser">
                <p class="selected-user-title">{{ selectedManagedUser.username }} / {{ roleName(selectedManagedUser.role) }}</p>
                <form @submit.prevent="updateManagedUser">
                  <label>显示名称<input v-model="userEditForm.displayName" /></label>
                  <label>角色
                    <select v-model="userEditForm.role">
                      <option value="ADMIN">系统管理员</option>
                      <option value="REVIEW_EXPERT">审图专家</option>
                      <option value="DESIGN_ENGINEER">设计工程师</option>
                      <option value="VIEWER">只读访客</option>
                    </select>
                  </label>
                  <label class="check-row"><input v-model="userEditForm.enabled" type="checkbox" /> 账号启用</label>
                  <button :disabled="userManagementLoading">保存用户</button>
                </form>
                <form class="password-reset-form" @submit.prevent="resetManagedPassword">
                  <label>重置密码<input v-model="resetPasswordForm.newPassword" type="password" autocomplete="new-password" /></label>
                  <button class="secondary" :disabled="userManagementLoading">重置并撤销其会话</button>
                </form>
              </template>
              <div v-else class="empty-state">
                <strong>请选择一个用户</strong>
                <p>从下方列表选择需要管理的账号。</p>
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="section-title">
              <h2>用户列表</h2>
              <button type="button" class="secondary" :disabled="userManagementLoading" @click="refreshManagedUsers">刷新</button>
            </div>
            <p v-if="userManagementMessage" class="hint">{{ userManagementMessage }}</p>
            <div class="report-table-wrap">
              <table class="report-table user-table">
                <thead>
                  <tr><th>用户</th><th>角色</th><th>状态</th><th>最后登录</th><th>密码更新时间</th><th>操作</th></tr>
                </thead>
                <tbody>
                  <tr v-if="!managedUsers.length"><td colspan="6">暂无用户</td></tr>
                  <tr v-for="managedUser in managedUsers" :key="managedUser.id" :class="{ selected: selectedManagedUserId === managedUser.id }">
                    <td><strong>{{ managedUser.displayName }}</strong><br /><small>{{ managedUser.username }}</small></td>
                    <td>{{ roleName(managedUser.role) }}</td>
                    <td><span :class="managedUser.enabled ? 'status-enabled' : 'status-disabled'">{{ managedUser.enabled ? '启用' : '停用' }}</span></td>
                    <td>{{ formatTime(managedUser.lastLoginAt) }}</td>
                    <td>{{ formatTime(managedUser.passwordChangedAt) }}</td>
                    <td><button type="button" class="secondary" @click="fillManagedUserForm(managedUser)">管理</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>
      </section>

      <section v-if="tab === 'audit' && can('AUDIT_VIEW')">
        <div class="panel">
          <div class="section-title">
            <div>
              <h2>审计日志</h2>
              <p class="hint">记录项目、图纸、版本、审查、整改、报告以及越权拒绝等关键操作。</p>
            </div>
            <strong>{{ auditPage?.total ?? 0 }} 条</strong>
          </div>
          <form class="audit-filters" @submit.prevent="refreshAudit(0)">
            <label>操作人<input v-model="auditFilters.actor" placeholder="例如 admin" /></label>
            <label>动作<input v-model="auditFilters.action" placeholder="例如 REVIEW_QUEUED" /></label>
            <label>对象类型<input v-model="auditFilters.targetType" placeholder="例如 task" /></label>
            <button :disabled="auditLoading">{{ auditLoading ? '查询中' : '查询' }}</button>
          </form>
          <p v-if="auditMessage" class="error">{{ auditMessage }}</p>
          <div class="report-table-wrap audit-table-wrap">
            <table class="report-table audit-table">
              <thead>
                <tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>详情</th></tr>
              </thead>
              <tbody>
                <tr v-if="!auditPage?.items.length"><td colspan="5">暂无匹配的审计记录</td></tr>
                <tr v-for="log in auditPage?.items ?? []" :key="log.id">
                  <td>{{ formatTime(log.createdAt) }}</td>
                  <td>{{ log.actor }}</td>
                  <td><strong>{{ log.action }}</strong></td>
                  <td>{{ log.targetType }} / {{ shortId(log.targetId) }}</td>
                  <td><code>{{ auditDetail(log.detailJson) }}</code></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="audit-pagination">
            <button type="button" class="secondary" :disabled="auditLoading || !auditPage || auditPage.page <= 0" @click="refreshAudit((auditPage?.page ?? 0) - 1)">上一页</button>
            <span>第 {{ (auditPage?.page ?? 0) + 1 }} / {{ Math.max(1, auditPage?.totalPages ?? 1) }} 页</span>
            <button type="button" class="secondary" :disabled="auditLoading || !auditPage || auditPage.page + 1 >= auditPage.totalPages" @click="refreshAudit((auditPage?.page ?? 0) + 1)">下一页</button>
          </div>
        </div>
      </section>

      <section v-if="tab === 'projects'">
        <div v-if="can('PROJECT_WRITE')" class="grid two">
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
            <label>项目<select v-model="drawingForm.projectId"><option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }} / {{ shortId(p.id) }}</option></select></label>
            <label>图号<input v-model="drawingForm.drawingNo" /></label>
            <label>图名<input v-model="drawingForm.title" /></label>
            <label>专业<input v-model="drawingForm.discipline" /></label>
            <button>创建图纸</button>
          </form>
        </div>
        <div v-else class="panel access-note">
          <strong>当前角色为只读项目视图</strong>
          <span>创建项目和图纸需要设计工程师或管理员权限。</span>
        </div>
        <form v-if="can('VERSION_UPLOAD')" class="panel inline" @submit.prevent="uploadVersion">
          <h2>上传CAD版本</h2>
          <select v-model="uploadForm.drawingId"><option v-for="d in drawings" :key="d.id" :value="d.id">{{ d.drawingNo }} {{ d.title }}</option></select>
          <input v-model="uploadForm.versionNo" />
          <input type="file" accept=".dxf,.dwg" @change="uploadForm.file = ($event.target as HTMLInputElement).files?.[0] ?? null" />
          <button>上传版本</button>
        </form>
        <div class="grid three">
          <div class="panel">
            <h2>项目</h2>
            <div v-for="p in projects" :key="p.id" class="item" :class="{ selected: selectedProject?.id === p.id }">
              <strong>{{ p.name }}</strong>
              <p>{{ p.shipNo }} / {{ p.owner }}</p>
              <div class="item-meta"><span>项目号 {{ shortId(p.id) }}</span><button type="button" class="secondary" @click="selectProject(p)">设为当前</button></div>
            </div>
          </div>
          <div class="panel">
            <h2>图纸</h2>
            <div v-for="d in selectedProjectDrawings" :key="d.id" class="item" :class="{ selected: selectedDrawing?.id === d.id }">
              <strong>{{ d.drawingNo }} {{ d.title }}</strong>
              <p>{{ d.discipline }} / 项目 {{ shortId(d.projectId) }}</p>
              <div class="item-meta"><span>记录号 {{ shortId(d.id) }}</span><button type="button" class="secondary" @click="selectDrawing(d)">设为当前</button></div>
            </div>
            <div v-if="!selectedProjectDrawings.length" class="empty-state">
              <strong>暂无图纸</strong>
              <p>当前项目还没有图纸记录。</p>
            </div>
          </div>
          <div class="panel">
            <h2>图纸版本</h2>
            <div v-for="v in selectedDrawingVersions" :key="v.id" class="item" :class="{ selected: currentVersion?.id === v.id }">
              <strong>{{ versionLabel(v) }}</strong>
              <p>{{ v.fileName }} / {{ v.parseStatus }} / 实体 {{ parseSummary(v).entityCount ?? 0 }}</p>
              <div class="item-meta"><span>记录号 {{ shortId(v.id) }}</span><button type="button" class="secondary" @click="selectVersion(v)">设为当前</button></div>
            </div>
            <div v-if="!selectedDrawingVersions.length" class="empty-state">
              <strong>暂无版本</strong>
              <p>当前图纸还没有上传 CAD 版本。</p>
            </div>
          </div>
        </div>
        <div v-if="can('PROJECT_MEMBER_MANAGE')" class="panel project-members-panel">
          <div class="section-title">
            <div>
              <h2>项目成员</h2>
              <p class="hint">{{ selectedProject ? `${selectedProject.name} / ${shortId(selectedProject.id)}` : '请先选择项目' }}</p>
            </div>
            <strong>{{ projectMembers.length }} 人</strong>
          </div>
          <form class="project-member-add" @submit.prevent="addProjectMember">
            <label>添加用户
              <select v-model="selectedProjectMemberUserId" :disabled="projectMemberLoading || !selectedProject">
                <option value="">请选择用户</option>
                <option v-for="user in availableProjectMemberUsers" :key="user.id" :value="user.id">
                  {{ user.displayName }} / {{ user.username }} / {{ roleName(user.role) }}
                </option>
              </select>
            </label>
            <button :disabled="projectMemberLoading || !selectedProjectMemberUserId">加入项目</button>
          </form>
          <p v-if="!availableProjectMemberUsers.length && selectedProject" class="hint">当前没有可继续添加的启用用户。</p>
          <p v-if="projectMemberMessage" class="hint">{{ projectMemberMessage }}</p>
          <div class="report-table-wrap">
            <table class="report-table project-member-table">
              <thead>
                <tr><th>成员</th><th>全局角色</th><th>账号状态</th><th>加入时间</th><th>添加人</th><th>操作</th></tr>
              </thead>
              <tbody>
                <tr v-if="!projectMembers.length"><td colspan="6">当前项目尚未分配成员</td></tr>
                <tr v-for="member in projectMembers" :key="member.id">
                  <td><strong>{{ member.displayName }}</strong><br /><small>{{ member.username }}</small></td>
                  <td>{{ roleName(member.role) }}</td>
                  <td><span :class="member.enabled ? 'status-enabled' : 'status-disabled'">{{ member.enabled ? '启用' : '停用' }}</span></td>
                  <td>{{ formatTime(member.createdAt) }}</td>
                  <td>{{ member.createdBy }}</td>
                  <td><button type="button" class="secondary" :disabled="projectMemberLoading" @click="removeProjectMember(member)">移除</button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="hint">项目成员关系只限定数据范围；成员能否上传、审查或关闭问题，仍由其全局角色权限决定。</p>
        </div>
      </section>

      <section v-if="tab === 'issues'">
        <form v-if="can('REVIEW_EXECUTE')" class="panel review-task-form" @submit.prevent="runReview">
          <h2>发起审查任务</h2>
          <div class="review-task-grid">
            <label>版本<select v-model="selectedReviewVersion"><option v-for="v in versions" :key="v.id" :value="v.id">{{ versionLabel(v) }}</option></select></label>
            <label>视觉置信度<input v-model.number="reviewOptions.visionConfidence" type="number" min="0.01" max="1" step="0.01" /></label>
            <label>OCR置信度<input v-model.number="reviewOptions.ocrConfidence" type="number" min="0" max="1" step="0.01" /></label>
            <label class="check-row"><input v-model="reviewOptions.autoVision" type="checkbox" /> 自动视觉证据</label>
            <label class="check-row"><input v-model="reviewOptions.autoOcr" type="checkbox" /> 自动OCR证据</label>
            <label class="check-row"><input v-model="reviewOptions.forceRender" type="checkbox" /> 强制重渲染</label>
            <button>发起审查</button>
          </div>
        </form>
        <div v-else class="panel access-note">
          <strong>审查任务由审图专家发起</strong>
          <span>你仍可查看任务、问题与证据；设计工程师可进入整改流程。</span>
        </div>
        <div class="panel">
          <h2>审查任务队列</h2>
          <div v-for="task in tasks" :key="task.id" class="task-row" :class="{ selected: selectedTaskDetailId === task.id }">
            <div class="task-main">
              <strong>{{ taskLabel(task) }}</strong>
              <p>阶段：{{ taskStageLabel(task) }}</p>
              <p>版本：{{ taskVersionLabel(task) }}</p>
              <p>{{ taskAutomationLabel(task) }}</p>
              <p v-if="task.errorMessage" class="error">{{ task.errorMessage }}</p>
              <div v-if="task.steps?.length" class="task-steps">
                <span v-for="step in task.steps" :key="step.id" :class="taskStepClass(step)" :title="step.message || step.stepCode">
                  <b>{{ step.stepName }}</b>
                  <small>{{ stepStatusLabel(step.status) }}</small>
                </span>
              </div>
            </div>
            <div class="task-actions">
              <button type="button" class="secondary" @click="selectTaskDetail(task)">详情</button>
              <button v-if="task.status === 'FAILED' && can('REVIEW_EXECUTE')" @click="retryTask(task)">重试</button>
            </div>
          </div>
        </div>
        <div v-if="selectedTaskDetail" class="panel task-detail-panel">
          <div class="section-title">
            <h2>审查任务详情</h2>
            <select v-model="selectedTaskDetailId">
              <option v-for="task in tasks" :key="task.id" :value="task.id">{{ taskLabel(task) }}</option>
            </select>
          </div>
          <div class="task-detail-grid">
            <div><span>状态</span><strong>{{ selectedTaskDetail.status }}</strong></div>
            <div><span>阶段</span><strong>{{ taskStageLabel(selectedTaskDetail) }}</strong></div>
            <div><span>版本</span><strong>{{ taskVersionLabel(selectedTaskDetail) }}</strong></div>
            <div><span>问题数</span><strong>{{ selectedTaskDetail.issueCount }}</strong></div>
            <div><span>证据数</span><strong>{{ selectedTaskDetailEvidenceCount }}</strong></div>
            <div><span>自动证据</span><strong>{{ taskAutomationLabel(selectedTaskDetail) }}</strong></div>
          </div>
          <p v-if="selectedTaskDetail.errorMessage" class="error">{{ selectedTaskDetail.errorMessage }}</p>
          <div v-if="selectedTaskDetail.steps?.length" class="task-step-detail-list">
            <div v-for="step in selectedTaskDetail.steps" :key="step.id" :class="taskStepClass(step)">
              <div class="task-step-title">
                <strong>{{ step.stepName }}</strong>
                <span>{{ stepStatusLabel(step.status) }}</span>
              </div>
              <p>{{ step.message || step.stepCode }}</p>
              <small>开始：{{ formatTime(step.startedAt) }} / 结束：{{ formatTime(step.finishedAt) }}</small>
              <code v-if="taskStepDetail(step)">{{ taskStepDetail(step) }}</code>
            </div>
          </div>
          <div v-else class="empty-state">
            <strong>暂无步骤记录</strong>
            <p>任务尚未进入后台执行，或旧任务没有生成步骤明细。</p>
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
            <form v-if="can('EVIDENCE_COLLECT')" class="vision-panel" @submit.prevent="runVisionDetection">
              <div class="diagnostic-title">
                <strong>YOLOv8视觉证据</strong>
                <span>可直接使用当前版本渲染图，也可上传PNG/JPG图像做人工对照。</span>
              </div>
              <div class="vision-grid">
                <label>图像<input type="file" accept=".png,.jpg,.jpeg" @change="visionForm.file = ($event.target as HTMLInputElement).files?.[0] ?? null" /></label>
                <label>置信度<input v-model.number="visionForm.confidence" type="number" min="0.01" max="1" step="0.01" /></label>
                <button type="button" :disabled="visionDetecting || !previewVersionId" @click="runVisionDetectionRendered">{{ visionDetecting ? '检测中' : '版本渲染图检测' }}</button>
                <button type="submit" class="secondary" :disabled="visionDetecting || !visionForm.file">{{ visionDetecting ? '检测中' : '上传图检测' }}</button>
              </div>
              <p v-if="visionMessage" class="hint">{{ visionMessage }}</p>
              <div v-if="previewVisionEvidences.length" class="vision-evidence-list">
                <strong>视觉证据</strong>
                <ul>
                  <li v-for="evidence in previewVisionEvidences" :key="evidence.id">{{ formatEvidence(evidence) }}</li>
                </ul>
              </div>
            </form>
            <form v-if="can('EVIDENCE_COLLECT')" class="ocr-panel" @submit.prevent="runOcrRecognition">
              <div class="diagnostic-title">
                <strong>OCR文字证据</strong>
                <span>可直接使用当前版本渲染图，也可上传PNG/JPG图像做人工对照。</span>
              </div>
              <div class="ocr-grid">
                <label>图像<input type="file" accept=".png,.jpg,.jpeg" @change="ocrForm.file = ($event.target as HTMLInputElement).files?.[0] ?? null" /></label>
                <label>置信度<input v-model.number="ocrForm.confidence" type="number" min="0" max="1" step="0.01" /></label>
                <button type="button" :disabled="ocrRecognizing || !previewVersionId" @click="runOcrRecognitionRendered">{{ ocrRecognizing ? '识别中' : '版本渲染图识别' }}</button>
                <button type="submit" class="secondary" :disabled="ocrRecognizing || !ocrForm.file">{{ ocrRecognizing ? '识别中' : '上传图识别' }}</button>
              </div>
              <p v-if="ocrMessage" class="hint">{{ ocrMessage }}</p>
              <div v-if="previewOcrEvidences.length" class="ocr-evidence-list">
                <strong>文字证据</strong>
                <ul>
                  <li v-for="evidence in previewOcrEvidences" :key="evidence.id">{{ formatEvidence(evidence) }}</li>
                </ul>
              </div>
            </form>
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
            <div v-if="selectedIssue" class="remediation-panel">
              <div class="remediation-head">
                <div>
                  <strong>{{ selectedIssue.title }}</strong>
                  <p>{{ selectedIssue.ruleCode }} / {{ selectedIssue.severity }} / {{ issueStatusLabel(selectedIssue.status) }}</p>
                </div>
                <span>{{ shortId(selectedIssue.id) }}</span>
              </div>
              <div v-if="can('ISSUE_REMEDIATE')" class="remediation-form">
                <label>经办人<input v-model="remediationForm.assignee" placeholder="例如：设计工程师A" /></label>
                <label>报告引用<input v-model="remediationForm.reportId" :placeholder="selectedIssueReportRef || '可选：先生成报告后自动引用'" /></label>
                <label class="remediation-note">整改/复核说明<textarea v-model="remediationForm.note" placeholder="记录整改措施、复核意见、关闭依据或重新打开原因"></textarea></label>
              </div>
              <div v-if="can('ISSUE_REMEDIATE')" class="actions remediation-actions">
                <button type="button" :disabled="remediationLoading" @click="applyIssueWorkflow('IN_PROGRESS')">开始整改</button>
                <button type="button" :disabled="remediationLoading" @click="applyIssueWorkflow('READY_FOR_REVIEW')">提交复核</button>
                <button v-if="can('ISSUE_REVIEW_DECIDE')" type="button" :disabled="remediationLoading" @click="applyIssueWorkflow('CLOSED')">关闭问题</button>
                <button v-if="can('ISSUE_REVIEW_DECIDE')" type="button" class="secondary" :disabled="remediationLoading" @click="applyIssueWorkflow('OPEN')">重新打开</button>
              </div>
              <p v-else class="hint">当前角色可查看整改时间线，但不能修改问题状态。</p>
              <p v-if="remediationMessage" class="hint">{{ remediationMessage }}</p>
              <div class="remediation-timeline">
                <strong>整改时间线</strong>
                <div v-if="!selectedIssueRecords.length" class="timeline-empty">暂无整改记录</div>
                <div v-for="record in selectedIssueRecords" :key="record.id" class="timeline-row">
                  <div>
                    <b>{{ remediationActionLabel(record.action) }}</b>
                    <span>{{ formatTime(record.createdAt) }} / {{ record.operator || '-' }}</span>
                  </div>
                  <p>{{ issueStatusLabel(record.fromStatus) }} → {{ issueStatusLabel(record.toStatus) }} / 经办人 {{ record.assignee || '-' }}</p>
                  <p v-if="record.reportId">报告引用：{{ shortId(record.reportId) }}</p>
                  <p v-if="record.note">{{ record.note }}</p>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">
              <strong>请选择一个问题</strong>
              <p>选中问题后可填写整改说明、提交复核、关闭问题并查看完整时间线。</p>
            </div>
            <div v-for="issue in issues" :key="issue.id" class="issue" :class="[issue.severity, { selected: selectedIssueId === issue.id }]" @click="selectIssue(issue)">
              <strong>{{ issue.title }}</strong>
              <p>{{ issue.ruleCode }} / {{ issue.severity }} / {{ issueStatusLabel(issue.status) }} / 图层 {{ issue.layerName || '-' }}</p>
              <p class="evidence">{{ issueEvidence(issue) }}</p>
              <div class="evidence-groups">
                <div v-for="group in groupedEvidence(issue)" :key="group.type" class="evidence-group">
                  <strong>{{ group.label }}</strong>
                  <ul>
                    <li v-for="item in group.items" :key="item">{{ item }}</li>
                  </ul>
                </div>
              </div>
              <p>{{ issue.description }}</p>
              <p>建议：{{ issue.suggestion }}</p>
              <div v-if="issue.aiExplanation" class="ai-explanation">
                <strong>AI辅助解释</strong>
                <p>{{ issue.aiExplanation.summary }}</p>
                <p>{{ issue.aiExplanation.reason }}</p>
                <p>{{ issue.aiExplanation.basis }}</p>
                <p>{{ issue.aiExplanation.recommendation }}</p>
                <p>{{ issue.aiExplanation.reviewFocus }}</p>
              </div>
              <div class="actions">
                <button type="button" @click.stop="selectIssue(issue)">{{ can('ISSUE_REMEDIATE') ? '处理' : '查看' }}</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="tab === 'reports'">
        <div class="grid two">
          <form v-if="can('REPORT_GENERATE')" class="panel" @submit.prevent="createReport">
            <h2>审查报告</h2>
            <label>任务<select v-model="selectedTask"><option v-for="t in tasks" :key="t.id" :value="t.id">{{ taskLabel(t) }}</option></select></label>
            <button>生成报告</button>
          </form>
          <div v-else class="panel access-note">
            <strong>报告生成需要审图权限</strong>
            <span>已生成报告仍可查看和下载；新报告由审图专家或管理员生成。</span>
          </div>
          <form class="panel" @submit.prevent="compareVersions">
            <h2>版本对比</h2>
            <label>旧版本<select v-model="compare.leftId"><option v-for="v in selectedDrawingVersions" :key="v.id" :value="v.id">{{ versionLabel(v) }}</option></select></label>
            <label>新版本<select v-model="compare.rightId"><option v-for="v in selectedDrawingVersions" :key="v.id" :value="v.id">{{ versionLabel(v) }}</option></select></label>
            <button>对比</button>
            <p v-if="compareMessage" class="hint">{{ compareMessage }}</p>
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
              <button type="button" @click="downloadReport">服务端下载.md</button>
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

        <article v-if="compareResult" class="compare-document">
          <header class="compare-header">
            <div>
              <p class="eyebrow">Version delta</p>
              <h2>版本差异摘要</h2>
              <span>{{ compareResult.summary }}</span>
            </div>
          </header>

          <div class="compare-metrics">
            <div><span>旧版实体</span><strong>{{ compareResult.left.entityCount }}</strong></div>
            <div><span>新版实体</span><strong>{{ compareResult.right.entityCount }}</strong></div>
            <div><span>实体变化</span><strong>{{ signedNumber(compareResult.entityCountDelta) }}</strong></div>
            <div><span>图层变化</span><strong>{{ compareLayerChangeCount }}</strong></div>
            <div><span>块变化</span><strong>{{ compareBlockChangeCount }}</strong></div>
            <div><span>文本变化</span><strong>{{ compareTextChangeCount }}</strong></div>
            <div><span>风险提示</span><strong>{{ compareRiskCount }}</strong></div>
          </div>

          <section class="compare-section">
            <h3>版本信息</h3>
            <table class="report-table compact-table">
              <thead>
                <tr><th>版本</th><th>文件</th><th>解析状态</th><th>实体</th><th>图层</th><th>文本</th><th>块参照</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td>{{ compareResult.left.versionNo }}</td>
                  <td>{{ compareResult.left.fileName }}</td>
                  <td>{{ compareResult.left.parseStatus }}</td>
                  <td>{{ compareResult.left.entityCount }}</td>
                  <td>{{ compareResult.left.layerCount }}</td>
                  <td>{{ compareResult.left.textCount }}</td>
                  <td>{{ compareResult.left.blockCount }}</td>
                </tr>
                <tr>
                  <td>{{ compareResult.right.versionNo }}</td>
                  <td>{{ compareResult.right.fileName }}</td>
                  <td>{{ compareResult.right.parseStatus }}</td>
                  <td>{{ compareResult.right.entityCount }}</td>
                  <td>{{ compareResult.right.layerCount }}</td>
                  <td>{{ compareResult.right.textCount }}</td>
                  <td>{{ compareResult.right.blockCount }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="compare-section grid two">
            <div>
              <h3>风险提示</h3>
              <ul class="report-list">
                <li v-for="hint in compareResult.riskHints" :key="hint">{{ hint }}</li>
              </ul>
            </div>
            <div>
              <h3>复核重点</h3>
              <ul class="report-list">
                <li v-for="focus in compareResult.reviewFocus" :key="focus">{{ focus }}</li>
              </ul>
            </div>
          </section>

          <section class="compare-section">
            <h3>结构变化</h3>
            <div class="compare-diff-grid">
              <div><span>新增图层</span><strong>{{ compareListText(compareResult.addedLayers) }}</strong></div>
              <div><span>删除图层</span><strong>{{ compareListText(compareResult.removedLayers) }}</strong></div>
              <div><span>新增空图层</span><strong>{{ compareListText(compareResult.addedEmptyLayers) }}</strong></div>
              <div><span>删除空图层</span><strong>{{ compareListText(compareResult.removedEmptyLayers) }}</strong></div>
              <div><span>新增块参照</span><strong>{{ compareListText(compareResult.addedBlocks) }}</strong></div>
              <div><span>删除块参照</span><strong>{{ compareListText(compareResult.removedBlocks) }}</strong></div>
            </div>
          </section>

          <section class="compare-section grid two">
            <div>
              <h3>实体类型变化</h3>
              <table class="report-table compact-table">
                <thead><tr><th>类型</th><th>旧</th><th>新</th><th>变化</th></tr></thead>
                <tbody>
                  <tr v-if="!compareResult.typeDeltas.length"><td colspan="4">无实体类型数量变化</td></tr>
                  <tr v-for="delta in compareResult.typeDeltas" :key="delta.name">
                    <td>{{ delta.name }}</td><td>{{ delta.leftCount }}</td><td>{{ delta.rightCount }}</td><td>{{ signedNumber(delta.delta) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div>
              <h3>图层实体变化</h3>
              <table class="report-table compact-table">
                <thead><tr><th>图层</th><th>旧</th><th>新</th><th>变化</th></tr></thead>
                <tbody>
                  <tr v-if="!compareResult.layerDeltas.length"><td colspan="4">无图层实体数量变化</td></tr>
                  <tr v-for="delta in compareResult.layerDeltas" :key="delta.name">
                    <td>{{ delta.name }}</td><td>{{ delta.leftCount }}</td><td>{{ delta.rightCount }}</td><td>{{ signedNumber(delta.delta) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="compare-section grid two">
            <div>
              <h3>新增文本</h3>
              <ul class="report-list">
                <li v-if="!compareResult.addedTexts.length">无新增文本</li>
                <li v-for="text in compareResult.addedTexts" :key="text">{{ text }}</li>
              </ul>
            </div>
            <div>
              <h3>删除文本</h3>
              <ul class="report-list">
                <li v-if="!compareResult.removedTexts.length">无删除文本</li>
                <li v-for="text in compareResult.removedTexts" :key="text">{{ text }}</li>
              </ul>
            </div>
          </section>
        </article>
      </section>
    </section>
  </main>
</template>
