import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { createTask, decomposeTask, deleteTask, listTasks, parseTask, updateTask } from './api'
import type { Decomposition, Page, Priority, Task, TaskInput, TaskStatus } from './types'
import './App.css'

const emptyDraft: TaskInput = {
  title: '',
  description: '',
  status: 'pending',
  priority: 'medium',
  tags: [],
}

const statusLabel: Record<TaskStatus, string> = {
  pending: '待处理',
  in_progress: '进行中',
  completed: '已完成',
}

const priorityLabel: Record<Priority, string> = {
  low: '低',
  medium: '中',
  high: '高',
}

function App() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({ query: '', status: '', priority: '', page: 0, smart: false })
  const [draft, setDraft] = useState<TaskInput>(emptyDraft)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [tags, setTags] = useState('')
  const [aiText, setAiText] = useState('')
  const [decompositions, setDecompositions] = useState<Record<number, Decomposition>>({})

  const queryKey = ['tasks', filters]
  const tasks = useQuery({
    queryKey,
    queryFn: () => listTasks({
      page: filters.page,
      query: filters.query || undefined,
      status: (filters.status || undefined) as TaskStatus | undefined,
      priority: (filters.priority || undefined) as Priority | undefined,
      smart: filters.smart,
    }),
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['tasks'] })

  const save = useMutation({
    mutationFn: () => {
      const input = { ...draft, tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean) }
      return editingId ? updateTask(editingId, input) : createTask(input)
    },
    onSuccess: () => {
      resetForm()
      refresh()
    },
  })

  const changeStatus = useMutation({
    mutationFn: ({ task, status }: { task: Task; status: TaskStatus }) =>
      updateTask(task.id, {
        title: task.title,
        description: task.description ?? undefined,
        status,
        priority: task.priority,
        dueAt: task.dueAt,
        tags: task.tags,
        version: task.version,
      }),
    onMutate: async ({ task, status }) => {
      await queryClient.cancelQueries({ queryKey: ['tasks'] })
      const previous = queryClient.getQueriesData<Page<Task>>({ queryKey: ['tasks'] })
      queryClient.setQueriesData<Page<Task>>({ queryKey: ['tasks'] }, (page) => page && ({
        ...page,
        content: page.content.map((item) => item.id === task.id ? { ...item, status } : item),
      }))
      return { previous }
    },
    onError: (_error, _variables, context) =>
      context?.previous.forEach(([key, data]) => queryClient.setQueryData(key, data)),
    onSettled: refresh,
  })

  const remove = useMutation({
    mutationFn: deleteTask,
    onSuccess: refresh,
  })

  const parse = useMutation({
    mutationFn: parseTask,
    onSuccess: (suggestion) => {
      const suggestedTags = suggestion.tags ?? []
      setDraft({
        title: suggestion.title,
        description: suggestion.description ?? '',
        status: 'pending',
        priority: suggestion.priority ?? 'medium',
        dueAt: suggestion.dueAt,
        tags: suggestedTags,
      })
      setTags(suggestedTags.join(', '))
      setAiText('')
    },
  })

  const decompose = useMutation({
    mutationFn: decomposeTask,
    onSuccess: (result, task) =>
      setDecompositions((current) => ({ ...current, [task.id]: result })),
  })

  function resetForm() {
    setDraft(emptyDraft)
    setTags('')
    setEditingId(null)
  }

  function edit(task: Task) {
    setEditingId(task.id)
    setDraft({
      title: task.title,
      description: task.description ?? '',
      status: task.status,
      priority: task.priority,
      dueAt: task.dueAt,
      tags: task.tags,
      version: task.version,
    })
    setTags(task.tags.join(', '))
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  const operationError = save.error || changeStatus.error || remove.error || parse.error || decompose.error

  return (
    <main>
      <header>
        <div>
          <span className="eyebrow">SMART WORKSPACE</span>
          <h1>清晰工作，智能推进</h1>
          <p>管理任务、依赖关系，并让 AI 帮你快速开始。</p>
        </div>
        <div className="task-count">
          <strong>{tasks.data?.totalElements ?? 0}</strong>
          <span>个任务</span>
        </div>
      </header>

      <section className="workspace">
        <div className="composer card">
          <div className="section-title">
            <div>
              <span className="eyebrow">{editingId ? 'EDIT TASK' : 'NEW TASK'}</span>
              <h2>{editingId ? '编辑任务' : '创建一个清晰的下一步'}</h2>
            </div>
            {editingId && <button className="text-button" onClick={resetForm}>取消编辑</button>}
          </div>

          <div className="ai-box">
            <span className="spark">✦</span>
            <input
              value={aiText}
              onChange={(event) => setAiText(event.target.value)}
              placeholder="例如：提醒我明天下午 3 点提交周报"
              aria-label="自然语言任务"
            />
            <button
              className="secondary"
              disabled={!aiText.trim() || parse.isPending}
              onClick={() => parse.mutate(aiText)}
            >
              {parse.isPending ? '理解中…' : 'AI 填写'}
            </button>
          </div>

          <form onSubmit={submit}>
            <label>
              标题
              <input
                required
                maxLength={200}
                value={draft.title}
                onChange={(event) => setDraft({ ...draft, title: event.target.value })}
                placeholder="要完成什么？"
              />
            </label>
            <label>
              描述
              <textarea
                maxLength={2000}
                value={draft.description}
                onChange={(event) => setDraft({ ...draft, description: event.target.value })}
                placeholder="补充背景或验收标准（可选）"
              />
            </label>
            <div className="form-row">
              <label>状态
                <select value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as TaskStatus })}>
                  {Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>优先级
                <select value={draft.priority} onChange={(event) => setDraft({ ...draft, priority: event.target.value as Priority })}>
                  {Object.entries(priorityLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>标签
                <input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="工作, 后端" />
              </label>
            </div>
            <button className="primary" disabled={save.isPending}>
              {save.isPending ? '保存中…' : editingId ? '保存修改' : '添加任务'}
            </button>
          </form>
        </div>
      </section>

      <section className="task-section">
        <div className="toolbar">
          <input
            className="search"
            value={filters.query}
            onChange={(event) => setFilters({ ...filters, query: event.target.value, page: 0 })}
            placeholder="搜索标题或描述…"
            aria-label="搜索任务"
          />
          <select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value, page: 0 })}>
            <option value="">全部状态</option>
            {Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <select value={filters.priority} onChange={(event) => setFilters({ ...filters, priority: event.target.value, page: 0 })}>
            <option value="">全部优先级</option>
            {Object.entries(priorityLabel).map(([value, label]) => <option key={value} value={value}>{label}优先级</option>)}
          </select>
          <button
            className={filters.smart ? 'smart-toggle active' : 'smart-toggle'}
            onClick={() => setFilters({ ...filters, smart: !filters.smart, page: 0 })}
            title="使用 ChromaDB 语义向量检索；不可用时自动降级"
          >
            ✦ 智能搜索
          </button>
        </div>

        {filters.smart && filters.query && tasks.data?.source && (
          <div className="search-source">
            {tasks.data.source === 'vector' ? 'ChromaDB 语义结果' : '向量服务不可用，已降级为关键词搜索'}
          </div>
        )}
        {(tasks.error || operationError) && (
          <div className="notice error">{(tasks.error || operationError)?.message}</div>
        )}
        {tasks.isLoading && <div className="notice">正在加载任务…</div>}
        {!tasks.isLoading && tasks.data?.content.length === 0 && (
          <div className="empty"><span>✓</span><h3>这里很清爽</h3><p>创建第一个任务，或调整筛选条件。</p></div>
        )}

        <div className="task-grid">
          {tasks.data?.content.map((task) => (
            <article className={`task-card ${task.status === 'completed' ? 'done' : ''}`} key={task.id}>
              <div className="task-meta">
                <span className={`priority ${task.priority}`}>{priorityLabel[task.priority]}优先级</span>
                <span className="task-id">#{task.id}</span>
              </div>
              <h3>{task.title}</h3>
              {task.description && <p>{task.description}</p>}
              <div className="tags">
                {task.tags.map((tag) => <span key={tag}>#{tag}</span>)}
                {task.dependencyIds.length > 0 && <span>依赖 {task.dependencyIds.length}</span>}
              </div>
              {decompositions[task.id] && (
                <div className="subtasks">
                  <strong>AI 建议步骤</strong>
                  {decompositions[task.id].subtasks.map((item) => <span key={item.title}>· {item.title}</span>)}
                </div>
              )}
              <div className="task-actions">
                <select
                  aria-label={`${task.title}的状态`}
                  value={task.status}
                  onChange={(event) => changeStatus.mutate({ task, status: event.target.value as TaskStatus })}
                >
                  {Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
                <button title="AI 拆解" onClick={() => decompose.mutate(task)}>✦</button>
                <button onClick={() => edit(task)}>编辑</button>
                <button className="danger" onClick={() => window.confirm(`删除“${task.title}”？`) && remove.mutate(task.id)}>删除</button>
              </div>
            </article>
          ))}
        </div>

        {!filters.smart && (tasks.data?.totalPages ?? 0) > 1 && (
          <nav className="pagination" aria-label="分页">
            <button disabled={filters.page === 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>上一页</button>
            <span>{filters.page + 1} / {tasks.data?.totalPages}</span>
            <button disabled={filters.page + 1 >= (tasks.data?.totalPages ?? 0)} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>下一页</button>
          </nav>
        )}
      </section>
    </main>
  )
}

export default App
