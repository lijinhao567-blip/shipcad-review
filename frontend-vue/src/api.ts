export type UserView = {
  id: string
  username: string
  displayName: string
  role: string
}

export type LoginResponse = {
  token: string
  expiresAt: string
  user: UserView
  permissions: string[]
}

export type AccessProfile = {
  user: UserView
  permissions: string[]
  sessionExpiresAt: string
}

export type ManagedUser = {
  id: string
  username: string
  displayName: string
  role: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  passwordChangedAt: string
  lastLoginAt: string | null
}

export type ProjectMember = {
  id: string
  projectId: string
  userId: string
  username: string
  displayName: string
  role: string
  enabled: boolean
  createdAt: string
  createdBy: string
}

export type AuditLog = {
  id: string
  actor: string
  action: string
  targetType: string
  targetId: string
  detailJson: string
  createdAt: string
}

export type AuditLogPage = {
  items: AuditLog[]
  total: number
  page: number
  size: number
  totalPages: number
}

export type HealthComponent = {
  status?: string
  mode?: string
  root?: string
  bucket?: string
  endpoint?: string
  cacheRoot?: string
  name?: string
  required?: boolean
  baseUrl?: string
  url?: string
  statusCode?: number
  capabilitiesStatusCode?: number
  activeCount?: number
  queuedCount?: number
  processingCount?: number
  localQueuedCount?: number
  remainingCapacity?: number
  workerRunning?: boolean
  error?: string
  health?: unknown
  capabilities?: unknown
}

export type SystemHealth = {
  status: string
  time: string
  database?: HealthComponent
  queue?: HealthComponent
  storage?: HealthComponent
  openapi?: HealthComponent
  workers?: Record<string, HealthComponent>
}

export type Project = {
  id: string
  name: string
  shipNo: string
  owner: string
  description: string
  createdAt: string
}

export type Drawing = {
  id: string
  projectId: string
  drawingNo: string
  title: string
  discipline: string
  createdAt: string
}

export type DrawingVersion = {
  id: string
  drawingId: string
  versionNo: string
  fileName: string
  fileSha256: string
  parseStatus: string
  parseSummaryJson: string
}

export type ReviewTask = {
  id: string
  versionId: string
  status: string
  stage: string | null
  issueCount: number
  startedAt: string | null
  finishedAt: string | null
  errorMessage: string | null
  autoVision?: boolean
  autoOcr?: boolean
  forceRender?: boolean
  visionConfidence?: number
  ocrConfidence?: number
  steps?: ReviewTaskStep[]
}

export type ReviewTaskStep = {
  id: string
  taskId: string
  stepOrder: number
  stepCode: string
  stepName: string
  status: string
  startedAt: string | null
  finishedAt: string | null
  message: string | null
  detailJson: string | null
}

export type ReviewIssue = {
  id: string
  taskId: string
  versionId: string
  ruleCode: string
  title: string
  description: string
  severity: string
  status: string
  layerName: string
  entityRef: string
  suggestion: string
  assignee: string
  evidences?: ReviewEvidence[]
  aiExplanation?: AiExplanation
}

export type RemediationRecord = {
  id: string
  issueId: string
  taskId: string
  versionId: string
  operator: string
  action: string
  fromStatus: string
  toStatus: string
  assignee: string
  reportId: string
  note: string
  createdAt: string
}

export type AiExplanation = {
  model: string
  summary: string
  reason: string
  basis: string
  recommendation: string
  reviewFocus: string
  evidenceRefs: string[]
}

export type ReviewEvidence = {
  id: string
  issueId: string
  taskId: string
  versionId: string
  ruleCode: string
  evidenceType: string
  sourceId: string
  sourceLabel: string
  summary: string
  payloadJson: string
  confidence: number
  createdAt: string
}

export type KnowledgeClause = {
  id: string
  code: string
  title: string
  content: string
  source: string
  tags: string
  remediationHint: string
  createdAt: string
}

export type ReportDocument = {
  id: string
  taskId: string
  versionId: string
  content: string
  createdAt: string
}

export type VersionCompareSide = {
  id: string
  versionNo: string
  fileName: string
  parseStatus: string
  entityCount: number
  layerCount: number
  textCount: number
  blockCount: number
}

export type VersionCountDelta = {
  name: string
  leftCount: number
  rightCount: number
  delta: number
}

export type VersionCompareResponse = {
  left: VersionCompareSide
  right: VersionCompareSide
  entityCountDelta: number
  addedLayers: string[]
  removedLayers: string[]
  addedEmptyLayers: string[]
  removedEmptyLayers: string[]
  addedBlocks: string[]
  removedBlocks: string[]
  addedTexts: string[]
  removedTexts: string[]
  layerDeltas: VersionCountDelta[]
  typeDeltas: VersionCountDelta[]
  riskHints: string[]
  reviewFocus: string[]
  summary: string
}

export type ParsedEntity = {
  id: string
  versionId: string
  entityType: string
  layerName: string
  textValue: string
  blockName: string
  x: number | null
  y: number | null
  geometry: Record<string, unknown>
}

export type Dashboard = {
  projectCount: number
  drawingCount: number
  versionCount: number
  taskCount: number
  openIssueCount: number
  issueCountBySeverity: Record<string, number>
  issueCountByStatus: Record<string, number>
  issueCountByRule: Record<string, number>
}

export type DownloadResult = {
  blob: Blob
  fileName: string | null
}

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export class ApiClient {
  token = localStorage.getItem('shipcad_token') ?? ''

  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers)
    if (!(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
    if (this.token) headers.set('Authorization', `Bearer ${this.token}`)
    const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      throw new Error(error.message ?? response.statusText)
    }
    return response.json() as Promise<T>
  }

  async blob(path: string, init: RequestInit = {}): Promise<Blob> {
    const headers = new Headers(init.headers)
    if (this.token) headers.set('Authorization', `Bearer ${this.token}`)
    const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      throw new Error(error.message ?? response.statusText)
    }
    return response.blob()
  }

  async download(path: string, init: RequestInit = {}): Promise<DownloadResult> {
    const headers = new Headers(init.headers)
    if (this.token) headers.set('Authorization', `Bearer ${this.token}`)
    const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      throw new Error(error.message ?? response.statusText)
    }
    return {
      blob: await response.blob(),
      fileName: fileNameFromContentDisposition(response.headers.get('Content-Disposition'))
    }
  }

  async login(username: string, password: string): Promise<LoginResponse> {
    const result = await this.request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    })
    this.token = result.token
    localStorage.setItem('shipcad_token', this.token)
    return result
  }

  clearToken(): void {
    this.token = ''
    localStorage.removeItem('shipcad_token')
  }
}

function fileNameFromContentDisposition(disposition: string | null): string | null {
  if (!disposition) return null
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try {
      return decodeURIComponent(encoded)
    } catch {
      return encoded
    }
  }
  const quoted = disposition.match(/filename="([^"]+)"/i)?.[1]
  if (quoted) return quoted
  return disposition.match(/filename=([^;]+)/i)?.[1]?.trim() ?? null
}

export const api = new ApiClient()
