export type TaskStatus = 'pending' | 'in_progress' | 'completed'
export type Priority = 'low' | 'medium' | 'high'

export interface Task {
  id: number
  title: string
  description: string | null
  status: TaskStatus
  priority: Priority
  dueAt: string | null
  createdAt: string
  updatedAt: string
  tags: string[]
  dependencyIds: number[]
  version: number
}

export interface TaskInput {
  title: string
  description?: string
  status?: TaskStatus
  priority?: Priority
  dueAt?: string | null
  tags?: string[]
  version?: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
  source?: 'vector' | 'keyword_fallback'
}

export interface TaskSuggestion extends TaskInput {
  source: 'llm' | 'rules'
}

export interface Decomposition {
  subtasks: Array<{ title: string; priority: Priority; tags: string[] }>
  source: 'llm' | 'rules'
}
