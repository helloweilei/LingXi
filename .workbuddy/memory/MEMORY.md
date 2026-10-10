# 灵犀创作 · 项目记忆

## 项目定位
面向中文网文作者的 AI 辅助小说创作平台（前后端分离）。Slogan：让灵感，落笔成章。

## 技术栈
- 前端：React 18 + TS + Vite 5 + Ant Design 5 + zustand + react-router 6 + axios + @uiw/react-md-editor
- 后端：Spring Boot 3.3.5（Java 17）+ Spring Security + JPA/Hibernate + WebClient(SSE) + JWT(jjwt 0.12.6) + Flyway + H2/MySQL 8
- 部署：Docker Compose（Nginx + Spring Boot + MySQL 8）；CI：GitHub Actions（mvn test + npm build）

## 架构要点
- 无状态后端可水平扩展；AI 走 OpenAI 兼容协议 + Mock 兜底；用户级/全局两级配置
- SSE 流式链路：WebClient → Flux<ServerSentEvent> → MVC 回写；Nginx 关闭缓冲
- 数据模型：User / Novel / Chapter(软删+版本快照≤50) / ChapterSummary / StoryMemory / Character / WorldSetting / AiConfig / WritingTask(状态机)
- 安全：JWT + 属主校验(越权返回404) + 登录限流(进程内，多实例需换 Redis) + 生产密钥兜底
- 自动创作流水线：QUEUED→RUNNING→WAITING_APPROVAL→DONE/FAILED/CANCELLED，2 线程池，支持断点续跑

## 规模
- 后端 72 个 Java 文件 ~4900 行，按模块分包（ai/auth/chapter/character/common/config/memory/novel/security/task/upload/user/world）
- 前端 src 仅 19 文件 ~3100 行 TS/TSX（集中式：7 页面 + 4 组件 + api 层 + store + layout）；NovelDetail.tsx 达 1108 行（过大）

## 已知短板
- 测试薄弱：前端 0 测试；后端 6 个测试文件；CI 不强制前端测试
- 文档滞后于实现：docs 标 v1.2(9-26)，实际已含封面上传 / 语言风格 / 断点续跑等后续功能
- 登录限流为进程内，多实例部署需换 Redis
- uploads 封面上传需确认是否做大小 / 类型校验
