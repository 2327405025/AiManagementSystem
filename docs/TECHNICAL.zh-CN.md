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
  repository/    JPA 数据访问
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
