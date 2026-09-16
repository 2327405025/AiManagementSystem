import type { Decomposition, Page, Priority, Task, TaskInput, TaskStatus, TaskSuggestion } from './types'

const API = import.meta.env.VITE_API_URL ?? ''
const TOKEN_KEY = 'taskmanager.token'
const USER_KEY = 'taskmanager.username'

export interface AuthSession {
  token: string
  userId: number
  username: string
}

let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUsername() {
  return localStorage.getItem(USER_KEY)
}

export function saveSession(session: AuthSession) {
  localStorage.setItem(TOKEN_KEY, session.token)
  localStorage.setItem(USER_KEY, session.username)
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

// Centralize JSON handling so every endpoint surfaces the backend Problem message.
// 集中处理 JSON，使所有端点都能向界面展示后端统一的 Problem 错误信息。
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken()
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  })
  if (response.status === 401) {
    clearSession()
    onUnauthorized?.()
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.message ?? `请求失败 (${response.status})`)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

export function registerAccount(username: string, password: string) {
  return request<AuthSession>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function loginAccount(username: string, password: string) {
  return request<AuthSession>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
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
    // Adapt semantic results to the page shape consumed by the dashboard.
    // 将语义搜索结果转换为工作台使用的统一分页结构。
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
