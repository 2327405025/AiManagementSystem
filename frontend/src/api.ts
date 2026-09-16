import type { Decomposition, Page, Priority, Task, TaskInput, TaskStatus, TaskSuggestion } from './types'

const API = import.meta.env.VITE_API_URL ?? ''

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.message ?? `请求失败 (${response.status})`)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

export interface TaskFilters {
  status?: TaskStatus
  priority?: Priority
  query?: string
  page: number
  smart?: boolean
}

export async function listTasks(filters: TaskFilters) {
  if (filters.smart && filters.query) {
    // Adapt semantic results to the same page shape consumed by the dashboard.
    const params = new URLSearchParams({ query: filters.query, limit: '20' })
    const result = await request<{ content: Task[]; source: 'vector' | 'keyword_fallback' }>(
      `/api/tasks/semantic-search?${params}`,
    )
    return {
      content: result.content,
      totalElements: result.content.length,
      totalPages: 1,
      page: 0,
      size: result.content.length,
      source: result.source,
    } satisfies Page<Task>
  }
  const params = new URLSearchParams({ page: String(filters.page), size: '9' })
  if (filters.status) params.set('status', filters.status)
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.query) params.set('query', filters.query)
  return request<Page<Task>>(`/api/tasks?${params}`)
}

export function createTask(input: TaskInput) {
  return request<Task>('/api/tasks', { method: 'POST', body: JSON.stringify(input) })
}

export function updateTask(id: number, input: TaskInput) {
  return request<Task>(`/api/tasks/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteTask(id: number) {
  return request<void>(`/api/tasks/${id}`, { method: 'DELETE' })
}

export function parseTask(text: string) {
  return request<TaskSuggestion>('/api/ai/parse-task', {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}

export function decomposeTask(task: Task) {
  return request<Decomposition>('/api/ai/decompose', {
    method: 'POST',
    body: JSON.stringify({ title: task.title, description: task.description }),
  })
}
