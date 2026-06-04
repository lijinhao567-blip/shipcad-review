export type LoginResponse = {
  token: string
  user: { id: string; username: string; displayName: string; role: string }
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
  evidences?: ReviewEvidence[]
  aiExplanation?: AiExplanation
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

  async login(username: string, password: string): Promise<LoginResponse> {
    const result = await this.request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    })
    this.token = result.token
    localStorage.setItem('shipcad_token', this.token)
    return result
  }
}

export const api = new ApiClient()
