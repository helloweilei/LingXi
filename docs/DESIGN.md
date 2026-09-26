# 灵犀创作 · 技术设计文档

> 版本：v1.1 ｜ 日期：2026-09-26（P0/P1 改造：Flyway、章节版本/软删除、鉴权加固、记忆上下文）

## 1. 总体架构

前后端分离，前端静态资源由 Nginx 托管并反代 `/api`，后端无状态可水平扩展，AI 走 OpenAI 兼容协议可替换任意供应商。

```
┌──────────────┐   /        ┌───────────────┐  /api/** (SSE/REST)  ┌──────────────────┐
│   Browser    │──────────▶ │ Nginx(前端容器) │────────────────────▶ │ Spring Boot 后端  │
│  React SPA   │  静态资源    │  gzip/try_files │◀───── SSE 流式 ───── │  业务/鉴权/AI编排  │
└──────────────┘            └───────────────┘                      └────────┬─────────┘
                                                                 │ JPA      │ WebClient(SSE)
                                                                 ▼          ▼
                                                          ┌──────────┐  ┌────────────────────┐
                                                          │ MySQL 8  │  │ LLM(OpenAI兼容/Mock)│
                                                          └──────────┘  └────────────────────┘
```

## 2. 技术选型

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 前端 | React 18 + TypeScript + Vite 5 | SPA，构建产物由 Nginx 托管 |
| UI | Ant Design 5 + @ant-design/icons | 中后台成熟组件库，中文本地化 |
| 状态/路由 | zustand（token/user 持久化）+ react-router 6 | 轻量全局状态 |
| AI 消费 | 原生 fetch + ReadableStream 解析 SSE | 支持带 Authorization 的 POST 流式 |
| 后端 | Spring Boot 3.3（Java 17），Maven 单模块 | spring-web(Servlet) + webflux 仅作 WebClient/Flux 能力 |
| ORM | Spring Data JPA + Hibernate（运行时 `ddl-auto: none`） | 表结构由 **Flyway** 管理迁移（`db/migration/V1/V2`），已有库自动 baseline |
| 数据库 | 默认 H2（文件模式，零依赖开箱即用）；生产 MySQL 8 | `application-prod.yml` 切换 |
| 鉴权 | JWT（jjwt 0.12，HS256）+ Spring Security 过滤器 | 无状态会话 |
| AI | OpenAI 兼容 `/chat/completions`（stream=true），Mock 引擎兜底 | 供应商可配 |

## 3. 数据模型

```
User(id, username UNIQUE, password, nickname, created_at)
Novel(id, user_id FK, title, description, genre, status, target_words,
      word_count, outline TEXT, cover_color, created_at, updated_at)
Chapter(id, novel_id FK, title, content TEXT, chapter_order, word_count,
        deleted BOOLEAN, created_at, updated_at)            -- 软删除
ChapterVersion(id, chapter_id FK, version_no, title, content TEXT,
               word_count, reason, created_at)             -- 章节快照（每章上限 50 版）
ChapterSummary(id, novel_id FK, chapter_id FK UNIQUE, summary TEXT, updated_at)  -- 章节 AI 摘要
StoryMemory(id, novel_id FK UNIQUE, story_brain TEXT, updated_at)                -- 故事脑
NovelCharacter(id, novel_id FK, name, role, description, created_at)
WorldSetting(id, novel_id FK, title, content TEXT, created_at)
```

- `Novel.word_count` 冗余汇总，在章节增/改/删（含软删/恢复/回滚）时增量维护，列表页零聚合查询。
- 章节采用**软删除**（`deleted` 标记），正文与版本快照保留，可恢复；删除作品时级联清理版本/摘要/故事脑。
- 章节内容/标题变更前自动快照到 `chapter_versions`（reason 记录 save/ai/restore），回滚版本前自动留档当前状态，保证可再回滚。

## 4. 接口设计（统一返回 `{code, message, data}`，业务错误 code≠0）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/register | 注册 {username,password,nickname} |
| POST | /api/auth/login | 登录 → {token,user} |
| GET | /api/auth/me | 当前用户 |
| GET/POST | /api/novels | 我的作品列表 / 新建 |
| GET/PUT/DELETE | /api/novels/{id} | 详情 / 更新（含大纲、状态）/ 删除 |
| GET/POST | /api/novels/{id}/chapters | 章节列表（不含正文）/ 新建 |
| GET/PUT/DELETE | /api/chapters/{id} | 章节正文读 / 保存（SaveReq 支持 reason 标注快照来源）/ 软删 |
| POST | /api/chapters/{id}/restore | 恢复已软删除章节 |
| GET | /api/chapters/{id}/versions | 章节版本历史（不含正文） |
| POST | /api/chapters/{id}/versions/{vid}/restore | 用指定快照回滚（回滚前自动留档） |
| GET | /api/novels/{id}/memory | 全书记忆：按章序的章节摘要 + 故事脑 |
| PUT | /api/novels/{id}/memory/story-brain | 覆盖更新故事脑 |
| GET/POST | /api/novels/{id}/characters | 角色列表 / 新建 |
| PUT/DELETE | /api/characters/{id} | 改 / 删 |
| GET/POST | /api/novels/{id}/worldsettings | 世界观列表 / 新建 |
| PUT/DELETE | /api/worldsettings/{id} | 改 / 删 |
| GET | /api/ai/status | AI 供应商标识（mock/openai）与模型名 |
| POST | /api/ai/novels/{id}/outline | 流式生成全书大纲 |
| POST | /api/ai/novels/{id}/chapters/{cid}/continue | 流式续写 |
| POST | /api/ai/novels/{id}/polish | 流式润色 {text} |
| POST | /api/ai/novels/{id}/expand | 流式扩写 {text} |
| POST | /api/ai/novels/{id}/chat | 流式对话 {message, history[]} |
| POST | /api/ai/novels/{id}/chapters/{cid}/summarize | 流式章节摘要（完成后自动落库覆盖） |

**SSE 事件约定**：`event:delta data:<增量文本>`（多条）；`event:done`（正常结束）；`event:error data:<错误信息>`。前端 `fetch` + ReadableStream 解析（EventSource 不支持 POST/自定义头）。

## 5. 关键设计点

### 5.1 AI 上下文与提示词

`ContextBuilder` 按 **Token 预算**（`lingxi.ai.context-budget-chars=8000`）打包上下文，优先级为：作品信息 → 一致性卡片（角色名册 + 世界观要点，纯规则生成）→ 大纲 → 故事脑 → 近期章节摘要（`max-recent-summaries=10`）；各段独立预算，超预算用"保头尾 + 中间省略"压缩。系统提示词固定为"中文网文创作助手"人设。Mock 引擎按任务类型 + 作品名/角色名生成示例文案，保证离线可演示。

### 5.2 鉴权与越权防护

- JWT 过滤器解析后按 `username` 查库挂 SecurityContext；`/api/auth/**` 放行，其余全部认证。
- 所有按 id 的资源访问统一走 `XxxService.load(id, currentUser)` 内做属主校验，不匹配一律 404，不泄露存在性。
- **登录/注册失败限流**（进程内 `LoginRateLimiter`）：同一 key（用户名+IP；注册按 IP）15 分钟内失败 5 次锁定 15 分钟，返回 429；登录成功清零。多实例部署需换 Redis 等共享存储。
- **生产密钥兜底**：`prod` profile 下 `JWT_SECRET` 未设置或使用默认密钥时拒绝启动（`JwtService` 抛 IllegalStateException），本地开发可用 `${LINGXI_JWT_SECRET:默认值}` 回退。

### 5.3 流式链路

`WebClient(bodyToFlux(String))` 解析上游 SSE → 转成 `Flux<ServerSentEvent>` 由 MVC（ReactiveTypeHandler）写回；上游异常映射为 `error` 事件；Mock 用 `delayElements` 模拟打字节奏。Nginx 反代需 `proxy_buffering off`。

### 5.4 配置切换

```
lingxi.ai.provider: mock | openai   # mock 内置示例引擎
lingxi.ai.base-url / api-key / model / temperature
spring.profiles.active: prod        # H2 → MySQL
```

## 6. 部署架构

- `backend/Dockerfile`：Maven 多阶段构建 → `eclipse-temurin:17-jre` 运行。
- `frontend/Dockerfile`：Node 构建静态产物 → `nginx:alpine` 托管并反代 `/api`（关闭缓冲以支持 SSE）。
- `docker-compose.yml`：MySQL 8（健康检查 + 数据卷）+ backend + frontend；`SPRING_PROFILES_ACTIVE=prod`。
- 本地开发：`mvn spring-boot:run`（8080，H2）+ `npm run dev`（5173，Vite 代理 /api）。
