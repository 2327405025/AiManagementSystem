# 技术文档

[English](TECHNICAL.en.md) · [返回中文 README](../README.md)

## 1. 运行环境
- Java 21、Spring Boot 3、Maven 3.9+
- React、TypeScript、Vite、Node.js 24+
- PostgreSQL 17：任务事实源
- Redis 7：可选共享二级缓存
- ChromaDB：可重建的向量索引
- Docker Desktop 与 Docker Compose

## 2. 模块结构
```text
backend/
  controller/    HTTP 参数与响应
  service/       业务规则与事务边界
  repository/    JPA 数据访问（对应 Mapper）
  security/      JWT 登录与当前用户
  domain/        实体和枚举
  cache/         Caffeine + Redis 两级缓存
  ai/            OpenAI 兼容 AI 与规则降级
  vector/        Chroma 索引、检索与校准
  health/        可选依赖健康状态
frontend/
  src/api.ts     HTTP 客户端
  src/App.tsx    查询、变更与交互界面
```

## 3. 配置
复制 `.env.example` 为 `.env`。真实密钥只写入 `.env`：

```env
OPENAI_API_KEY=你的_DeepSeek_API_Key
OPENAI_BASE_URL=https://api.deepseek.com/v1
OPENAI_MODEL=deepseek-flash
```

如果账号控制台提供不同模型名，以控制台为准。其他关键变量：
- `DB_POOL_MAX`：数据库连接池上限，默认 16。
- `CACHE_LOCAL_MAX_SIZE`：Caffeine 最大条目数，默认 10000。
- `CACHE_LOCAL_TTL` / `CACHE_REDIS_TTL`：两级缓存有效期。
- `AI_POOL_*` / `INDEX_POOL_*`：有界线程池大小与队列容量。
- `CHROMA_ENABLED` / `CHROMA_URL`：向量索引开关与地址。

### 模板配置与私人配置
- `.env.example` 是可公开提交的通用模板，只包含空 Key、非敏感默认值和变量名称。
- `.env` 是你机器上的私人配置，写入真实 DeepSeek Key；`.gitignore` 已忽略 `.env` 及其他 `.env.*` 文件，并单独放行 `.env.example`。
- Docker Compose 在启动时读取根目录 `.env`，不会要求应用把密钥写入源码或镜像。
- 不要把 Key 写入任何 `VITE_*` 变量；Vite 会把它们打包进浏览器资源，访问网页的人都能看到。

提交前可执行：

```powershell
git check-ignore .env  # 应输出 .env
git ls-files .env      # 应无输出
git status --short     # 不应出现 .env
```

如果密钥曾被提交，即使之后删除文件也不代表安全，因为它仍可能存在于 Git 历史和他人的克隆中；应立即在 DeepSeek 控制台撤销并重新生成密钥。

## 4. 数据与一致性
- Flyway 按顺序执行 `V1`–`V3`，创建任务、依赖、乐观锁、索引和幂等记录。
- `tasks.version` 通过 JPA `@Version` 检测并发覆盖。
- `task_dependencies` 使用复合主键和双外键；服务层拒绝自依赖与间接环。
- `Idempotency-Key` 与请求体哈希持久化，数据库主键解决并发竞争。
- 写事务提交后才失效缓存并发布索引事件，回滚数据不会进入派生存储。

## 5. 缓存与检索
- 主键读取采用 Cache-Aside：Caffeine L1 → Redis L2 → PostgreSQL。
- Caffeine 原子加载合并同 Key 并发未命中，防止缓存击穿。
- Redis TTL 增加随机抖动，故障时直接回源 PostgreSQL。
- 普通 UI 使用 offset 分页；深分页使用 `(created_at, id)` 键集游标。
- Chroma 不可用时，语义检索自动降级到数据库关键词过滤。

## 6. AI 调用
后端请求 `${OPENAI_BASE_URL}/chat/completions`，使用 Bearer Key 和 JSON Output。AI 仅返回建议，不直接写入数据库。未配置 Key、请求超时、限流或熔断时，系统返回确定性的本地规则结果；响应中的 `source` 为 `llm` 或 `rules`。

## 7. 本地与 Docker 命令
```powershell
# 完整栈
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
docker compose logs -f backend

# 停止但保留数据
docker compose down
```

本地开发可分别运行：

```powershell
cd backend
mvn spring-boot:run

cd ..\frontend
npm install
npm run dev
```

## 8. 验证与可观测性
```powershell
cd backend
mvn test

cd ..\frontend
npm run lint
npm run build
```

- `/livez`：只检查进程存活。
- `/readyz`：数据库必须可用；Redis 故障显示 `DEGRADED` 但返回 HTTP 200。
- `/actuator/prometheus`：JVM、HTTP、HikariCP、Caffeine 与 Resilience4j 指标。

## 9. 故障排查
- AI 返回 `rules`：检查 Key、模型名、账户余额和 `docker compose logs -f backend`。
- `/readyz` 返回 503：优先检查 PostgreSQL 容器和迁移日志。
- 语义搜索回退：检查 Chroma 健康状态；任务 CRUD 不受影响。
- 首次启动较慢：Docker 需要下载镜像，Chroma 还需准备嵌入模型。
