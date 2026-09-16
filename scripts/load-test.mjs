const baseUrl = process.env.BASE_URL ?? 'http://localhost:8080'
const requests = Number(process.env.REQUESTS ?? 1000)
const concurrency = Number(process.env.CONCURRENCY ?? 25)

const created = await fetch(`${baseUrl}/api/tasks`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ title: 'Load test target', priority: 'medium', tags: ['benchmark'] }),
})
if (!created.ok) throw new Error(`Setup failed: ${created.status}`)
const task = await created.json()

const latencies = []
let failures = 0
let next = 0

async function worker() {
  while (next < requests) {
    next += 1
    const started = performance.now()
    try {
      const response = await fetch(`${baseUrl}/api/tasks/${task.id}`)
      if (!response.ok) failures += 1
      await response.arrayBuffer()
    } catch {
      failures += 1
    }
    latencies.push(performance.now() - started)
  }
}

const wallStart = performance.now()
await Promise.all(Array.from({ length: concurrency }, worker))
const wallSeconds = (performance.now() - wallStart) / 1000
latencies.sort((a, b) => a - b)
const percentile = (p) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * p))]

console.log(JSON.stringify({
  requests,
  concurrency,
  failures,
  throughputRps: Math.round(requests / wallSeconds),
  p50Ms: Number(percentile(0.5).toFixed(2)),
  p95Ms: Number(percentile(0.95).toFixed(2)),
  p99Ms: Number(percentile(0.99).toFixed(2)),
}, null, 2))
