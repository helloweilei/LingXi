# 灵犀创作 · 技术设计文档

> 版本：v1.0 ｜ 日期：2026-09-22

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
| ORM | Spring Data JPA + Hibernate（ddl-auto: update） | 小团队快速迭代；生产可换 Flyway |
| 数据库 | 默认 H2（文件模式，零依赖开箱即用）；生产 MySQL 8 | `application-prod.yml` 切换 |
| 鉴权 | JWT（jjwt 0.12，HS256）+ Spring Security 过滤器 | 无状态会话 |
| AI | OpenAI 兼容 `/chat/completions`（stream=true），Mock 引擎兜底 | 供应商可配 |

## 3. 数据模型

```
User(id, username UNIQUE, password, nickname, created_at)
Novel(id, user_id FK, title, description, genre, status, target_words,
      word_count, outline TEXT, cover_color, created_at, updated_at)
Chapter(id, novel_id FK, title, content TEXT, chapter_order, word_count, created_at, updated_at)
NovelCharacter(id, novel_id FK, name, role, description, created_at)
WorldSetting(id, novel_id FK, title, content TEXT, created_at)
```

- `Novel.word_count` 冗余汇总，在章节增/改/删时增量维护，列表页零聚合查询。
- 所有实体逻辑删除不引入（v1 直接物理删除，正文有自动保存兜底）。

## 4. 接口设计（统一返回 `{code, message, data}`，业务错误 code≠0）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/register | 注册 {username,password,nickname} |
| POST | /api/auth/login | 登录 → {token,user} |
| GET | /api/auth/me | 当前用户 |
| GET/POST | /api/novels | 我的作品列表 / 新建 |
| GET/PUT/DELETE | /api/novels/{id} | 详情 / 更新（含大纲、状态）/ 删除 |
| GET/POST | /api/novels/{id}/chapters | 章节列表（不含正文）/ 新建 |
| GET/PUT/DELETE | /api/chapters/{id} | 章节正文读 / 保存 / 删 |
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

**SSE 事件约定**：`event:delta data:<增量文本>`（多条）；`event:done`（正常结束）；`event:error data:<错误信息>`。前端 `fetch` + ReadableStream 解析（EventSource 不支持 POST/自定义头）。

## 5. 关键设计点

### 5.1 AI 上下文与提示词

系统提示词固定为"中文网文创作助手"人设 + 结构化作品设定（书名/类型/简介/大纲/角色/世界观）；用户提示词按任务拼装（续写带上文尾部、润色带原文、对话带最近 10 轮历史）。Mock 引擎按任务类型 + 作品名/角色名生成示例文案，保证离线可演示。

### 5.2 鉴权与越权防护

- JWT 过滤器解析后按 `username` 查库挂 SecurityContext；`/api/auth/**` 放行，其余全部认证。
- 所有按 id 的资源访问统一走 `XxxService.load(id, currentUser)` 内做属主校验，不匹配一律 404，不泄露存在性。

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
