import childProcess from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import zlib from 'node:zlib'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const ROOT = path.resolve(__dirname, '..')
const DEFAULT_SAMPLE = path.join(ROOT, 'datasets', 'parser', 'cases', 'dense_deck_grid.dxf')
const DEFAULT_RUN_DIR = path.join(ROOT, '.run')

function isInsideDirectory(parent, candidate) {
  const relative = path.relative(path.resolve(parent), path.resolve(candidate))
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
}

function resolveRunArtifactPath(value, defaultFileName) {
  const input = String(value ?? '').trim()
  const runDir = path.resolve(DEFAULT_RUN_DIR)
  let candidate
  if (!input) {
    candidate = path.join(runDir, defaultFileName)
  } else if (path.isAbsolute(input)) {
    candidate = path.resolve(input)
  } else {
    const fromRoot = path.resolve(ROOT, input)
    candidate = isInsideDirectory(runDir, fromRoot) ? fromRoot : path.resolve(runDir, input)
  }
  if (!isInsideDirectory(runDir, candidate)) {
    throw new Error(`Smoke output path must stay under ${runDir}: ${input}`)
  }
  return candidate
}

function safeReportString(value, maxLength = 500) {
  return String(value ?? '')
    .replace(/[^\p{L}\p{N}\s._:/\\@+=,-]/gu, '_')
    .slice(0, maxLength)
}

function safeReportNumber(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function safeReportList(values, maxItems = 50) {
  return Array.isArray(values) ? values.slice(0, maxItems).map((item) => safeReportString(item, 120)) : []
}

function safeReportValue(value, depth = 0) {
  if (depth > 5) return safeReportString(value, 120)
  if (value == null || typeof value === 'boolean') return value
  if (typeof value === 'number') return safeReportNumber(value)
  if (typeof value === 'string') return safeReportString(value)
  if (Array.isArray(value)) return value.slice(0, 100).map((item) => safeReportValue(item, depth + 1))
  if (typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .slice(0, 100)
        .map(([key, item]) => [safeReportString(key, 120), safeReportValue(item, depth + 1)]),
    )
  }
  return safeReportString(value)
}

function parseArgs(argv) {
  const args = {
    backendUrl: 'http://127.0.0.1:8080',
    frontendUrl: 'http://127.0.0.1:5173',
    username: 'admin',
    password: 'admin123',
    sample: DEFAULT_SAMPLE,
    browserPath: process.env.BROWSER_PATH || '',
    browserDebugPort: 9333,
    timeoutMs: 60000,
    minForegroundRatio: 0.001,
    minForegroundPixels: 300,
    minUniqueSampleColors: 3,
    output: path.join(DEFAULT_RUN_DIR, 'dxf-viewer-webgl-smoke.json'),
    screenshot: path.join(DEFAULT_RUN_DIR, 'dxf-viewer-webgl-smoke.png'),
    headed: false,
  }
  for (let index = 0; index < argv.length; index += 1) {
    const item = argv[index]
    const next = () => {
      index += 1
      if (index >= argv.length) throw new Error(`${item} requires a value`)
      return argv[index]
    }
    if (item === '--backend-url') args.backendUrl = next()
    else if (item === '--frontend-url') args.frontendUrl = next()
    else if (item === '--username') args.username = next()
    else if (item === '--password') args.password = next()
    else if (item === '--sample') args.sample = path.resolve(next())
    else if (item === '--browser-path') args.browserPath = next()
    else if (item === '--browser-debug-port') args.browserDebugPort = Number(next())
    else if (item === '--timeout-ms') args.timeoutMs = Number(next())
    else if (item === '--min-foreground-ratio') args.minForegroundRatio = Number(next())
    else if (item === '--min-foreground-pixels') args.minForegroundPixels = Number(next())
    else if (item === '--min-unique-sample-colors') args.minUniqueSampleColors = Number(next())
    else if (item === '--output') args.output = resolveRunArtifactPath(next(), 'dxf-viewer-webgl-smoke.json')
    else if (item === '--screenshot') args.screenshot = resolveRunArtifactPath(next(), 'dxf-viewer-webgl-smoke.png')
    else if (item === '--headed') args.headed = true
    else if (item === '--help') {
      printHelp()
      process.exit(0)
    } else {
      throw new Error(`Unknown argument: ${item}`)
    }
  }
  return args
}

function printHelp() {
  console.log(`Usage: node tools/run_dxf_viewer_webgl_smoke.mjs [options]

Options:
  --backend-url URL              Backend API URL. Default: http://127.0.0.1:8080
  --frontend-url URL             Vue frontend URL. Default: http://127.0.0.1:5173
  --sample PATH                  DXF sample to upload. Default: datasets/parser/cases/dense_deck_grid.dxf
  --browser-path PATH            Chrome/Edge/Chromium executable. Can also use BROWSER_PATH.
  --browser-debug-port PORT      DevTools port. Default: 9333
  --headed                       Run a visible browser instead of headless.
  --output PATH                  JSON report path under .run. Default: .run/dxf-viewer-webgl-smoke.json
  --screenshot PATH              Preview crop path under .run. Default: .run/dxf-viewer-webgl-smoke.png
  --min-foreground-ratio NUMBER  Minimum pixels that must differ from the background. Default: 0.001
  --min-foreground-pixels NUMBER Minimum foreground pixel count. Default: 300
  --min-unique-sample-colors N   Minimum sampled color variety. Default: 3
`)
}

function findBrowser(browserPath) {
  const candidates = [
    browserPath,
    process.env.CHROME_PATH,
    process.env.EDGE_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
  ].filter(Boolean)
  const found = candidates.find((candidate) => fs.existsSync(candidate))
  if (!found) {
    throw new Error('Chrome, Edge, or Chromium executable was not found. Pass --browser-path or set BROWSER_PATH.')
  }
  return found
}

async function apiJson(baseUrl, pathName, token, init = {}) {
  const headers = new Headers(init.headers ?? {})
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(`${baseUrl}${pathName}`, { ...init, headers })
  if (!response.ok) {
    const detail = await response.text()
    throw new Error(`${init.method ?? 'GET'} ${pathName} failed with ${response.status}: ${detail}`)
  }
  return response.json()
}

async function createSmokeVersion(args) {
  const baseUrl = args.backendUrl.replace(/\/$/, '')
  const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
  const runId = `${stamp}-${crypto.randomBytes(3).toString('hex')}`
  const login = await apiJson(baseUrl, '/api/auth/login', '', {
    method: 'POST',
    body: JSON.stringify({ username: args.username, password: args.password }),
  })
  const token = login.token
  const project = await apiJson(baseUrl, '/api/projects', token, {
    method: 'POST',
    body: JSON.stringify({
      name: `DXF Viewer Smoke ${runId}`,
      shipNo: `SMOKE-${runId}`,
      owner: 'Automated Smoke',
      description: 'Generated by tools/run_dxf_viewer_webgl_smoke.mjs',
    }),
  })
  const drawing = await apiJson(baseUrl, '/api/drawings', token, {
    method: 'POST',
    body: JSON.stringify({
      projectId: project.id,
      drawingNo: `SMOKE-DXF-${runId}`,
      title: 'DXF Viewer WebGL Smoke',
      discipline: 'Hull Structure',
    }),
  })

  const form = new FormData()
  const sampleBytes = await fs.promises.readFile(args.sample)
  form.set('drawingId', drawing.id)
  form.set('versionNo', 'SMOKE-A')
  form.set('file', new Blob([sampleBytes], { type: 'application/dxf' }), path.basename(args.sample))
  const version = await apiJson(baseUrl, '/api/versions/upload', token, { method: 'POST', body: form })
  const parsed = await apiJson(baseUrl, `/api/versions/parse?versionId=${encodeURIComponent(version.id)}`, token, { method: 'POST' })
  const entities = await apiJson(baseUrl, `/api/versions/${encodeURIComponent(version.id)}/entities`, token)
  return { token, project, drawing, version: parsed, entityCount: entities.length }
}

async function waitFor(predicate, timeoutMs, intervalMs = 250) {
  const started = Date.now()
  let lastError
  while (Date.now() - started < timeoutMs) {
    try {
      const value = await predicate()
      if (value) return value
    } catch (error) {
      lastError = error
    }
    await delay(intervalMs)
  }
  throw new Error(`Timed out after ${timeoutMs}ms${lastError ? `: ${lastError.message}` : ''}`)
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function launchBrowser(args) {
  const browser = findBrowser(args.browserPath)
  const profileDir = path.join(DEFAULT_RUN_DIR, `dxf-viewer-smoke-profile-${Date.now()}`)
  fs.mkdirSync(profileDir, { recursive: true })
  const browserArgs = [
    `--remote-debugging-port=${args.browserDebugPort}`,
    `--user-data-dir=${profileDir}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--disable-extensions',
    '--enable-webgl',
    '--ignore-gpu-blocklist',
    '--use-gl=swiftshader',
    'about:blank',
  ]
  if (!args.headed) browserArgs.unshift('--headless=new')
  if (process.platform === 'linux') browserArgs.unshift('--no-sandbox')

  const stdout = fs.openSync(path.join(DEFAULT_RUN_DIR, 'dxf-viewer-smoke-browser.out.log'), 'a')
  const stderr = fs.openSync(path.join(DEFAULT_RUN_DIR, 'dxf-viewer-smoke-browser.err.log'), 'a')
  const processHandle = childProcess.spawn(browser, browserArgs, { stdio: ['ignore', stdout, stderr] })
  return { processHandle, profileDir }
}

async function browserTarget(debugPort, timeoutMs) {
  const endpoint = `http://127.0.0.1:${debugPort}/json/list`
  return waitFor(async () => {
    const response = await fetch(endpoint)
    if (!response.ok) return null
    const targets = await response.json()
    return targets.find((target) => target.type === 'page' && target.webSocketDebuggerUrl) ?? null
  }, timeoutMs)
}

function frontendUrlWithSmokeFlag(frontendUrl) {
  const url = new URL(frontendUrl)
  url.searchParams.set('dxf-preview-smoke', '1')
  return url.toString()
}

class CdpSession {
  constructor(socket) {
    this.socket = socket
    this.nextId = 1
    this.pending = new Map()
    this.socket.addEventListener('message', (event) => this.handleMessage(event))
  }

  static async connect(url) {
    if (typeof WebSocket !== 'function') {
      throw new Error('This script requires a Node.js runtime with global WebSocket support.')
    }
    const socket = new WebSocket(url)
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true })
      socket.addEventListener('error', reject, { once: true })
    })
    return new CdpSession(socket)
  }

  handleMessage(event) {
    const message = JSON.parse(event.data)
    if (!message.id) return
    const pending = this.pending.get(message.id)
    if (!pending) return
    this.pending.delete(message.id)
    if (message.error) pending.reject(new Error(`${message.error.message}: ${message.error.data ?? ''}`))
    else pending.resolve(message.result ?? {})
  }

  send(method, params = {}) {
    const id = this.nextId++
    const payload = JSON.stringify({ id, method, params })
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.socket.send(payload)
    })
  }

  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
      userGesture: true,
    })
    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.text ?? 'Runtime.evaluate failed')
    }
    return result.result?.value
  }

  close() {
    this.socket.close()
  }
}

async function runBrowserSmoke(args, token, versionId) {
  const launched = launchBrowser(args)
  const frontendSmokeUrl = frontendUrlWithSmokeFlag(args.frontendUrl)
  let cdp
  try {
    const target = await browserTarget(args.browserDebugPort, args.timeoutMs)
    cdp = await CdpSession.connect(target.webSocketDebuggerUrl)
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Page.navigate', { url: frontendSmokeUrl })
    await waitFor(() => cdp.evaluate('document.readyState === "complete" || document.readyState === "interactive"'), args.timeoutMs)
    await cdp.evaluate(`localStorage.setItem('shipcad_token', ${JSON.stringify(token)}); location.reload()`)
    await waitFor(() => cdp.evaluate('document.readyState === "complete" || document.readyState === "interactive"'), args.timeoutMs)
    await waitFor(
      () => cdp.evaluate(`document.body && document.body.textContent.includes('已登录')`),
      args.timeoutMs,
    )
    const switched = await cdp.evaluate(`(() => {
      const button = Array.from(document.querySelectorAll('button')).find((item) => item.textContent.trim() === '问题闭环')
      if (!button) return false
      button.click()
      return true
    })()`)
    if (!switched) throw new Error('Could not find 问题闭环 navigation button')

    await waitFor(
      () => cdp.evaluate(`Boolean(document.querySelector('.preview-panel select'))`),
      args.timeoutMs,
    )
    let lastSelection = null
    try {
      await waitFor(async () => {
        lastSelection = await cdp.evaluate(`(() => {
          const select = document.querySelector('.preview-panel select')
          if (!select) return { ok: false, reason: 'missing preview select' }
          const option = Array.from(select.options).find((item) => item.value === ${JSON.stringify(versionId)})
          if (!option) return { ok: false, reason: 'version option missing', optionCount: select.options.length }
          select.value = ${JSON.stringify(versionId)}
          select.dispatchEvent(new Event('change', { bubbles: true }))
          return { ok: true, optionCount: select.options.length }
        })()`)
        return lastSelection?.ok ? lastSelection : null
      }, args.timeoutMs, 500)
    } catch (error) {
      throw new Error(`Could not select smoke version: ${JSON.stringify(lastSelection)}; ${error.message}`)
    }

    const state = await waitFor(
      async () => {
        const value = await cdp.evaluate(`(() => {
          const meta = document.querySelector('.dxf-meta')?.textContent || ''
          const error = document.querySelector('.viewer-error')?.textContent || ''
          const previewState = document.querySelector('.preview-state')?.textContent || ''
          const host = document.querySelector('.dxf-webgl-host')
          const canvas = host?.querySelector('canvas')
          const layers = Array.from(document.querySelectorAll('.dxf-meta .layer-row span')).map((item) => item.textContent || '')
          return {
            ready: meta.includes('dxf-viewer') && meta.includes('加载完成') && Boolean(canvas) && canvas.width > 0 && canvas.height > 0 && layers.length > 0 && !error,
            meta,
            error,
            previewState,
            layerCount: layers.length,
            layers,
            hasHost: Boolean(host),
            hasCanvas: Boolean(canvas),
            canvasWidth: canvas?.width ?? 0,
            canvasHeight: canvas?.height ?? 0
          }
        })()`)
        return value?.ready ? value : null
      },
      args.timeoutMs,
      500,
    )

    await cdp.evaluate(`document.querySelector('.dxf-webgl-host')?.scrollIntoView({ block: 'center', inline: 'center' })`)
    await delay(500)
    await cdp.evaluate(`document.querySelector('.viewer-tools button')?.click()`)
    await delay(1000)
    const canvasPixels = await readCanvasPixels(cdp)
    if (
      canvasPixels.foregroundPixels < args.minForegroundPixels ||
      canvasPixels.foregroundRatio < args.minForegroundRatio ||
      canvasPixels.uniqueSampleColors < args.minUniqueSampleColors
    ) {
      throw new Error(`Preview WebGL canvas appears blank: ${JSON.stringify(canvasPixels)}`)
    }
    const rect = await cdp.evaluate(`(() => {
      const canvas = document.querySelector('.dxf-webgl-host canvas')
      if (!canvas) return null
      const rect = canvas.getBoundingClientRect()
      return {
        x: Math.max(0, Math.round(rect.x)),
        y: Math.max(0, Math.round(rect.y)),
        width: Math.max(1, Math.min(Math.round(rect.width), window.innerWidth - Math.max(0, Math.round(rect.x)))),
        height: Math.max(1, Math.min(Math.round(rect.height), window.innerHeight - Math.max(0, Math.round(rect.y))))
      }
    })()`)
    if (!rect || rect.width < 50 || rect.height < 50) throw new Error(`Invalid preview canvas bounds: ${JSON.stringify(rect)}`)
    const screenshotDataUrl = await cdp.evaluate(`(() => {
      const canvas = document.querySelector('.dxf-webgl-host canvas')
      if (!canvas) return ''
      return canvas.toDataURL('image/png')
    })()`)
    if (!screenshotDataUrl.startsWith('data:image/png;base64,')) {
      throw new Error('Preview canvas did not produce a PNG data URL')
    }
    const screenshotBytes = Buffer.from(screenshotDataUrl.slice('data:image/png;base64,'.length), 'base64')
    await fs.promises.mkdir(path.dirname(args.screenshot), { recursive: true })
    await fs.promises.writeFile(args.screenshot, screenshotBytes)
    const pixels = analyzePng(screenshotBytes)
    if (
      pixels.foregroundPixels < args.minForegroundPixels ||
      pixels.foregroundRatio < args.minForegroundRatio ||
      pixels.uniqueSampleColors < args.minUniqueSampleColors
    ) {
      throw new Error(`Preview screenshot appears blank: ${JSON.stringify({ screenshot: pixels, canvas: canvasPixels })}`)
    }
    return { state, rect, pixels, canvasPixels, screenshot: args.screenshot }
  } finally {
    if (cdp) cdp.close()
    launched.processHandle.kill()
    await new Promise((resolve) => {
      const timer = setTimeout(resolve, 1000)
      launched.processHandle.once('exit', () => {
        clearTimeout(timer)
        resolve()
      })
    })
    await fs.promises.rm(launched.profileDir, { recursive: true, force: true }).catch(() => undefined)
  }
}

async function readCanvasPixels(cdp) {
  return cdp.evaluate(`(() => {
    const canvas = document.querySelector('.dxf-webgl-host canvas')
    if (!canvas) return { ok: false, reason: 'missing canvas' }
    const gl = canvas.getContext('webgl2') || canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
    if (!gl) return { ok: false, reason: 'missing webgl context', width: canvas.width, height: canvas.height }
    gl.finish()
    const width = gl.drawingBufferWidth
    const height = gl.drawingBufferHeight
    const data = new Uint8Array(width * height * 4)
    gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, data)
    const background = [data[0], data[1], data[2]]
    let foregroundPixels = 0
    let nonWhitePixels = 0
    const unique = new Set()
    const sampleStep = Math.max(1, Math.floor((width * height) / 10000))
    for (let offset = 0, pixel = 0; offset < data.length; offset += 4, pixel += 1) {
      const r = data[offset]
      const g = data[offset + 1]
      const b = data[offset + 2]
      if (!(r > 245 && g > 245 && b > 245)) nonWhitePixels += 1
      const distance = Math.sqrt((r - background[0]) ** 2 + (g - background[1]) ** 2 + (b - background[2]) ** 2)
      if (distance > 12) foregroundPixels += 1
      if (pixel % sampleStep === 0) unique.add(r + ',' + g + ',' + b)
    }
    const pixels = width * height
    return {
      ok: true,
      width,
      height,
      pixels,
      nonWhitePixels,
      nonWhiteRatio: Number((nonWhitePixels / pixels).toFixed(6)),
      foregroundPixels,
      foregroundRatio: Number((foregroundPixels / pixels).toFixed(6)),
      backgroundColor: background,
      uniqueSampleColors: unique.size
    }
  })()`)
}

function analyzePng(buffer) {
  const signature = buffer.subarray(0, 8).toString('hex')
  if (signature !== '89504e470d0a1a0a') throw new Error('Screenshot is not a PNG')
  let offset = 8
  let width = 0
  let height = 0
  let bitDepth = 0
  let colorType = 0
  const idat = []
  while (offset < buffer.length) {
    const length = buffer.readUInt32BE(offset)
    const type = buffer.subarray(offset + 4, offset + 8).toString('ascii')
    const data = buffer.subarray(offset + 8, offset + 8 + length)
    if (type === 'IHDR') {
      width = data.readUInt32BE(0)
      height = data.readUInt32BE(4)
      bitDepth = data.readUInt8(8)
      colorType = data.readUInt8(9)
      if (data.readUInt8(12) !== 0) throw new Error('Interlaced PNG screenshots are not supported')
    } else if (type === 'IDAT') {
      idat.push(data)
    } else if (type === 'IEND') {
      break
    }
    offset += 12 + length
  }
  if (bitDepth !== 8) throw new Error(`Unsupported PNG bit depth: ${bitDepth}`)
  const bytesPerPixel = { 0: 1, 2: 3, 4: 2, 6: 4 }[colorType]
  if (!bytesPerPixel) throw new Error(`Unsupported PNG color type: ${colorType}`)
  const inflated = zlib.inflateSync(Buffer.concat(idat))
  const stride = width * bytesPerPixel
  let inputOffset = 0
  let previous = Buffer.alloc(stride)
  let nonWhitePixels = 0
  let foregroundPixels = 0
  let backgroundColor = null
  const unique = new Set()
  for (let y = 0; y < height; y += 1) {
    const filter = inflated[inputOffset]
    inputOffset += 1
    const row = Buffer.from(inflated.subarray(inputOffset, inputOffset + stride))
    inputOffset += stride
    applyPngFilter(row, previous, bytesPerPixel, filter)
    for (let x = 0; x < width; x += 1) {
      const index = x * bytesPerPixel
      const [r, g, b] = colorType === 0 ? [row[index], row[index], row[index]] : [row[index], row[index + 1], row[index + 2]]
      if (!backgroundColor) backgroundColor = [r, g, b]
      if (!(r > 245 && g > 245 && b > 245)) nonWhitePixels += 1
      if (colorDistance([r, g, b], backgroundColor) > 12) foregroundPixels += 1
      if ((x + y * width) % Math.max(1, Math.floor((width * height) / 10000)) === 0) {
        unique.add(`${r},${g},${b}`)
      }
    }
    previous = row
  }
  const pixels = width * height
  return {
    width,
    height,
    pixels,
    nonWhitePixels,
    nonWhiteRatio: Number((nonWhitePixels / pixels).toFixed(6)),
    foregroundPixels,
    foregroundRatio: Number((foregroundPixels / pixels).toFixed(6)),
    backgroundColor,
    uniqueSampleColors: unique.size,
  }
}

function colorDistance(left, right) {
  return Math.sqrt(
    (left[0] - right[0]) ** 2 +
    (left[1] - right[1]) ** 2 +
    (left[2] - right[2]) ** 2,
  )
}

function applyPngFilter(row, previous, bytesPerPixel, filter) {
  for (let index = 0; index < row.length; index += 1) {
    const left = index >= bytesPerPixel ? row[index - bytesPerPixel] : 0
    const up = previous[index] ?? 0
    const upLeft = index >= bytesPerPixel ? previous[index - bytesPerPixel] : 0
    if (filter === 0) continue
    if (filter === 1) row[index] = (row[index] + left) & 0xff
    else if (filter === 2) row[index] = (row[index] + up) & 0xff
    else if (filter === 3) row[index] = (row[index] + Math.floor((left + up) / 2)) & 0xff
    else if (filter === 4) row[index] = (row[index] + paeth(left, up, upLeft)) & 0xff
    else throw new Error(`Unsupported PNG filter type: ${filter}`)
  }
}

function paeth(left, up, upLeft) {
  const estimate = left + up - upLeft
  const leftDistance = Math.abs(estimate - left)
  const upDistance = Math.abs(estimate - up)
  const upLeftDistance = Math.abs(estimate - upLeft)
  if (leftDistance <= upDistance && leftDistance <= upLeftDistance) return left
  return upDistance <= upLeftDistance ? up : upLeft
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  fs.mkdirSync(DEFAULT_RUN_DIR, { recursive: true })
  if (!fs.existsSync(args.sample)) throw new Error(`DXF sample does not exist: ${args.sample}`)
  const sampleHash = crypto.createHash('sha256').update(await fs.promises.readFile(args.sample)).digest('hex')
  const created = await createSmokeVersion(args)
  const browser = await runBrowserSmoke(args, created.token, created.version.id)
  const report = {
    ok: true,
    time: new Date().toISOString(),
    smoke: 'dxf-viewer-webgl',
    backendUrl: safeReportString(args.backendUrl),
    frontendUrl: safeReportString(args.frontendUrl),
    sample: safeReportString(args.sample),
    sampleSha256: sampleHash,
    assertions: {
      authenticatedUpload: true,
      cadWorkerParse: true,
      frontendBlobLoad: true,
      officialPreviewLoaded: true,
      webglCanvasNonBlank: true,
    },
    artifacts: {
      screenshot: safeReportString(args.screenshot),
    },
  }
  await fs.promises.mkdir(path.dirname(args.output), { recursive: true })
  await fs.promises.writeFile(args.output, JSON.stringify(report, null, 2) + '\n', 'utf8')
  console.log(`DXF viewer WebGL smoke passed: version=${created.version.id} layers=${browser.state.layerCount} nonWhiteRatio=${browser.pixels.nonWhiteRatio}`)
  console.log(`Report: ${args.output}`)
  console.log(`Screenshot: ${args.screenshot}`)
}

async function writeFailureReport(error) {
  console.error(`DXF viewer WebGL smoke failed: ${error.message}`)
  process.exit(1)
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  main().catch(writeFailureReport)
}

export {
  DEFAULT_RUN_DIR,
  analyzePng,
  apiJson,
  browserTarget,
  delay,
  frontendUrlWithSmokeFlag,
  launchBrowser,
  readCanvasPixels,
  resolveRunArtifactPath,
  safeReportList,
  safeReportNumber,
  safeReportString,
  safeReportValue,
  waitFor,
  CdpSession,
}
