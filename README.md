# 灵犀创作 ✒️

> **让灵感，落笔成章。** —— AI 小说创作平台（前后端分离）

灵犀创作是一款面向中文网络文学作者的 AI 辅助写作平台：从灵感、大纲、角色与世界观设定，到逐章正文写作，AI 全程参与——生成全书大纲、续写正文、润色与扩写文本、对话式头脑风暴。

## 功能特性

- 🔐 **账户体系**：注册 / 登录（JWT），数据按用户隔离
- 📚 **作品管理**：多本书管理，类型 / 简介 / 目标字数 / 状态，字数与进度统计
- 📝 **章节写作**：沉浸式编辑器，自动保存（防抖 2.5s）+ Ctrl+S，实时字数统计
- 👤 **设定库**：角色设定（主角/配角/反派）与世界观条目，AI 写作时自动注入上下文
- 🧭 **全书大纲**：手动编写或 AI 一键生成，支持分卷 + 章节梗概结构
- 🕘 **章节版本历史与回滚**：每次内容/标题变更自动生成快照（每章最多保留 50 版），章节支持软删除与恢复，可一键回滚到任意历史版本（回滚前自动留档）
- 🧠 **记忆上下文**：章节 AI 摘要自动沉淀 + 可维护的"故事脑"全书设定；AI 写作时按 Token 预算注入近期摘要与一致性卡片，长书不丢设定
- 🤖 **AI 能力**（全部 SSE 流式输出，可中途停止，确认后才写入正文）：
  - **AI 续写**：结合全书设定、上一章结尾与本章已有内容，从光标处续写
  - **AI 润色 / 扩写**：选中文字即可改写，确认后替换所选内容
  - **AI 摘要**：为指定章节流式生成 150-200 字摘要，自动写入记忆库
  - **AI 助手**：结合作品上下文的多轮创作对话
- 🚀 **AI 自动创作**：一键流水线——（可选）先生成全书大纲，再逐章生成完整正文并落库（自动快照），可选为每章生成摘要沉淀记忆。支持**全自动**一口气写完，或**逐章确认**（每章完成后等你批准再继续）；任务进度实时可见，可随时取消，同一本书同一时间只允许一个进行中任务
- ⚙️ **用户级模型配置**：右上角账号菜单「模型配置」即可填写自己的接口地址 / API Key / 模型名（如 DeepSeek），配置按用户隔离、保存后立即生效，无需改任何配置文件；未配置时自动使用内置示例引擎
- 🔌 **AI 供应商可替换**：内置离线示例引擎（Mock，无需任何 Key 即可体验全流程）；既可全局配置，也可每个用户各自接入任意 OpenAI 兼容接口（DeepSeek / 通义 / Kimi / OpenAI…）

## 技术栈

| 端 | 技术 |
| --- | --- |
| 前端 | React 18 + TypeScript + Vite 5 + Ant Design 5 + zustand |
| 后端 | Spring Boot 3.3（Java 17）+ Spring Security + JPA + WebClient（SSE 流式） |
| 数据库 | 默认 H2（文件模式，零配置）；生产 MySQL 8（`prod` profile）。表结构由 **Flyway 管理迁移**（V1 初始表 + V2 版本/记忆表 + V3 模型配置/自动创作任务表） |
| 部署 | Docker Compose（Nginx + Spring Boot + MySQL） |

详细设计见 [docs/PRD.md](docs/PRD.md) 与 [docs/DESIGN.md](docs/DESIGN.md)。

## 快速开始（本地开发）

前置要求：JDK 17+、Node.js 18+、Maven 3.8+。

```bash
# 1. 启动后端（8080，默认 H2 文件数据库，数据落在 backend/data/）
cd backend
mvn spring-boot:run

# 2. 启动前端（5173，已配置 /api 代理到 8080）
cd frontend
npm install
npm run dev
```

浏览器访问 <http://localhost:5173>，注册账号即可开始创作。默认 AI 为**示例引擎（Mock）**，无需任何 API Key。

> Windows 下可直接双击 `start-dev.bat` 一键启动前后端。

## Docker 一键部署

```bash
docker compose up -d --build
# 访问 http://localhost
```

包含 MySQL 8（数据持久化卷）+ 后端（prod profile）+ 前端（Nginx 托管静态资源并反代 `/api`，已关闭缓冲以支持 SSE 流式）。详见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 接入真实大模型

**方式一（推荐，按用户配置）**：登录后在右上角账号菜单 →「模型配置」填写接口地址、API Key、模型名（如 `https://api.deepseek.com` + `deepseek-chat`），点击「测试连接」验证后保存即生效。配置仅对当前账号生效，其他人仍走示例引擎或各自的配置。

**方式二（全局配置）**：修改 `backend/src/main/resources/application.yml`（或通过环境变量覆盖），作为所有未单独配置用户的兜底：

```yaml
lingxi:
  ai:
    provider: openai                 # mock -> openai
    base-url: https://api.deepseek.com/v1   # 任意 OpenAI 兼容地址
    api-key: sk-xxxxxxxx
    model: deepseek-chat
```

Docker 部署时在 `docker-compose.yml` 中设置 `LINGXI_AI_PROVIDER=openai`、`LINGXI_AI_API_KEY=...` 等环境变量即可。

## 生产安全清单

- [ ] 设置环境变量 `LINGXI_JWT_SECRET` 为足够长的随机串（**必填**：`prod` profile 下未设置或使用默认密钥时后端将拒绝启动，本地开发可用默认值）
- [ ] 修改 MySQL 密码（compose 与 `application-prod.yml` 保持一致）
- [ ] 如需公网访问，为 Nginx 配置 HTTPS
- [ ] 登录接口已内置失败限流（15 分钟内 5 次失败锁定 15 分钟）；如需更高防护可在网关层叠加

> CI：GitHub Actions（`.github/workflows/ci.yml`）在 push/PR 时自动执行后端 `mvn test` 与前端 `npm run build`。
