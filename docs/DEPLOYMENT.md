# 灵犀创作 · 部署文档

## 一、部署架构

```
浏览器 ──> Nginx(frontend 容器, :80)
            ├── /          → 静态资源 (React 构建产物, try_files SPA)
            └── /api/**    → proxy_pass http://backend:8080  (proxy_buffering off, 支持 SSE)
backend 容器 (Spring Boot, prod profile)
            └── JDBC → mysql 容器 (MySQL 8, 数据卷持久化)
```

## 二、Docker Compose 部署（推荐）

前置要求：Docker 20+ 与 Docker Compose v2。

```bash
cd 灵犀创作
docker compose up -d --build
```

启动顺序：MySQL（健康检查通过）→ backend → frontend。完成后访问 `http://<服务器IP>/`。

常用命令：

```bash
docker compose ps              # 查看状态
docker compose logs -f backend # 跟随后端日志
docker compose down            # 停止（保留数据卷）
docker compose down -v         # 停止并清空数据
```

### 环境变量（backend 容器）

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| SPRING_PROFILES_ACTIVE | 固定为 prod（MySQL） | prod |
| MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD | 数据库连接 | mysql/3306/lingxi/lingxi/lingxi123 |
| LINGXI_AI_PROVIDER | `mock`（示例引擎）或 `openai`（OpenAI 兼容） | mock |
| LINGXI_AI_BASE_URL | OpenAI 兼容接口地址 | https://api.deepseek.com/v1 |
| LINGXI_AI_API_KEY | API Key | 空（mock） |
| LINGXI_AI_MODEL | 模型名 | deepseek-chat |

> JWT 密钥默认取 `application.yml` 中的值，生产环境建议在 backend 容器追加环境变量 `LINGXI_JWT_SECRET=<64位随机串>` 覆盖。

## 三、传统部署（物理机 / 虚机）

### 后端

```bash
cd backend
mvn package -DskipTests
# 内置 H2（文件模式）直接运行：
java -jar target/lingxi-backend-1.0.0.jar
# 或连接外部 MySQL：
java -jar target/lingxi-backend-1.0.0.jar --spring.profiles.active=prod \
     --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/lingxi?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" \
     --spring.datasource.username=lingxi --spring.datasource.password=你的密码
```

建议用 systemd / NSSM 注册为系统服务。端口 8080。

### 前端

```bash
cd frontend
npm install && npm run build
# 产物在 dist/，部署到任意 Nginx 静态站点
```

Nginx 站点配置参考 `frontend/nginx.conf`（将 `proxy_pass http://backend:8080` 改为实际后端地址）。**注意 `proxy_buffering off;` 必须保留，否则 AI 流式输出会被 Nginx 缓冲成一次性返回。**

## 四、数据库说明

- 表结构由 Hibernate `ddl-auto: update` 自动创建/增量更新，首次启动即完成建表。
- H2 开发库位于 `backend/data/lingxi.mv.db`，删除该文件即重置数据。
- 生产 MySQL 建议定期 `mysqldump` 备份 `lingxi` 库；Docker 数据卷 `mysql-data` 即全部数据。

## 五、升级与回滚

```bash
git pull
docker compose up -d --build   # 重新构建镜像并滚动替换
```

回滚：`git checkout <上一版本号>` 后重新执行上面的构建命令（数据卷不受影响）。

## 六、常见问题

| 现象 | 排查 |
| --- | --- |
| AI 输出一次性弹出而不是打字机效果 | Nginx 未关闭缓冲，检查 `proxy_buffering off` |
| AI 报“调用失败” | `provider=openai` 时检查 `api-key`/`base-url`/`model` 与网络连通性 |
| 登录后反复跳回登录页 | JWT 密钥被修改过（旧 token 失效），重新登录即可 |
| 前端页面 404/白屏 | Nginx `try_files ... /index.html` 未配置，SPA 路由回退缺失 |
| 数据库连接失败 | 确认 MySQL 容器健康：`docker compose ps`，核对用户名密码 |
