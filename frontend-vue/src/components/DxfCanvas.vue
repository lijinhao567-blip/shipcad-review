<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { ParsedEntity, ReviewIssue } from '../api'

const props = defineProps<{
  entities: ParsedEntity[]
  issues: ReviewIssue[]
  selectedIssueId?: string
}>()

const canvas = ref<HTMLCanvasElement | null>(null)

const selectedIssue = computed(() => props.issues.find((issue) => issue.id === props.selectedIssueId))
const issueEntityIds = computed(() => new Set(props.issues.map((issue) => issue.entityRef).filter(Boolean)))
const issueLayers = computed(() => new Set(props.issues.map((issue) => issue.layerName).filter(Boolean)))

function pointsOf(entity: ParsedEntity): Array<[number, number]> {
  const g = entity.geometry || {}
  const kind = g.kind
  if (kind === 'line') return [tuple(g.start), tuple(g.end)]
  if (kind === 'circle' || kind === 'arc') {
    const center = tuple(g.center)
    const radius = Number(g.radius || 0)
    return [[center[0] - radius, center[1] - radius], [center[0] + radius, center[1] + radius]]
  }
  if (kind === 'polyline') return Array.isArray(g.points) ? g.points.map(tuple) : []
  if (kind === 'text' || kind === 'insert') return [tuple(g.insert)]
  if (entity.x != null && entity.y != null) return [[entity.x, entity.y]]
  return []
}

function tuple(value: unknown): [number, number] {
  if (Array.isArray(value) && value.length >= 2) return [Number(value[0]), Number(value[1])]
  return [0, 0]
}

function render() {
  const el = canvas.value
  if (!el) return
  const ctx = el.getContext('2d')
  if (!ctx) return

  const dpr = window.devicePixelRatio || 1
  const width = el.clientWidth || 900
  const height = el.clientHeight || 520
  el.width = Math.floor(width * dpr)
  el.height = Math.floor(height * dpr)
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)

  const allPoints = props.entities.flatMap(pointsOf)
  if (!allPoints.length) {
    ctx.fillStyle = '#667085'
    ctx.font = '16px Microsoft YaHei'
    ctx.fillText('暂无可预览图元', 24, 36)
    return
  }

  const xs = allPoints.map((point) => point[0])
  const ys = allPoints.map((point) => point[1])
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  const pad = 32
  const scale = Math.min((width - pad * 2) / Math.max(1, maxX - minX), (height - pad * 2) / Math.max(1, maxY - minY))
  const toCanvas = (point: [number, number]): [number, number] => [
    pad + (point[0] - minX) * scale,
    height - pad - (point[1] - minY) * scale
  ]

  drawGrid(ctx, width, height)
  for (const entity of props.entities) {
    drawEntity(ctx, entity, toCanvas, scale, isHighlighted(entity), isSelected(entity))
  }
}

function drawGrid(ctx: CanvasRenderingContext2D, width: number, height: number) {
  ctx.strokeStyle = '#eef2f6'
  ctx.lineWidth = 1
  for (let x = 0; x < width; x += 40) {
    ctx.beginPath()
    ctx.moveTo(x, 0)
    ctx.lineTo(x, height)
    ctx.stroke()
  }
  for (let y = 0; y < height; y += 40) {
    ctx.beginPath()
    ctx.moveTo(0, y)
    ctx.lineTo(width, y)
    ctx.stroke()
  }
}

function isHighlighted(entity: ParsedEntity): boolean {
  return issueEntityIds.value.has(entity.id) || issueLayers.value.has(entity.layerName)
}

function isSelected(entity: ParsedEntity): boolean {
  const issue = selectedIssue.value
  if (!issue) return false
  return issue.entityRef === entity.id || (!!issue.layerName && issue.layerName === entity.layerName)
}

function drawEntity(
  ctx: CanvasRenderingContext2D,
  entity: ParsedEntity,
  toCanvas: (point: [number, number]) => [number, number],
  scale: number,
  highlighted: boolean,
  selected: boolean
) {
  const g = entity.geometry || {}
  ctx.save()
  ctx.strokeStyle = selected ? '#dc2626' : highlighted ? '#f59e0b' : '#1f2937'
  ctx.fillStyle = selected ? '#dc2626' : highlighted ? '#b45309' : '#334155'
  ctx.lineWidth = selected ? 3 : highlighted ? 2 : 1.2

  if (g.kind === 'line') {
    const a = toCanvas(tuple(g.start))
    const b = toCanvas(tuple(g.end))
    ctx.beginPath()
    ctx.moveTo(a[0], a[1])
    ctx.lineTo(b[0], b[1])
    ctx.stroke()
  } else if (g.kind === 'circle') {
    const c = toCanvas(tuple(g.center))
    ctx.beginPath()
    ctx.arc(c[0], c[1], Math.max(2, Number(g.radius || 0) * scale), 0, Math.PI * 2)
    ctx.stroke()
  } else if (g.kind === 'arc') {
    const c = toCanvas(tuple(g.center))
    const start = -Number(g.endAngle || 0) * Math.PI / 180
    const end = -Number(g.startAngle || 0) * Math.PI / 180
    ctx.beginPath()
    ctx.arc(c[0], c[1], Math.max(2, Number(g.radius || 0) * scale), start, end)
    ctx.stroke()
  } else if (g.kind === 'polyline' && Array.isArray(g.points)) {
    const points = g.points.map(tuple).map(toCanvas)
    ctx.beginPath()
    points.forEach((point, index) => index ? ctx.lineTo(point[0], point[1]) : ctx.moveTo(point[0], point[1]))
    ctx.stroke()
  } else if (g.kind === 'text') {
    const p = toCanvas(tuple(g.insert))
    ctx.font = `${selected ? 15 : 12}px Microsoft YaHei`
    ctx.fillText(entity.textValue || 'TEXT', p[0], p[1])
  } else if (g.kind === 'insert') {
    const p = toCanvas(tuple(g.insert))
    ctx.strokeRect(p[0] - 6, p[1] - 6, 12, 12)
    ctx.font = '11px Microsoft YaHei'
    ctx.fillText(entity.blockName || 'BLOCK', p[0] + 8, p[1] - 8)
  }
  ctx.restore()
}

watch(() => [props.entities, props.issues, props.selectedIssueId], render, { deep: true })
onMounted(render)
</script>

<template>
  <canvas ref="canvas" class="dxf-canvas"></canvas>
</template>
