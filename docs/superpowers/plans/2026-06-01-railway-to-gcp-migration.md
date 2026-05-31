# Railway → Google Cloud Run 迁移计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 warehouse-backend（Spring Boot + PostgreSQL）从 Railway 迁移到 Google Cloud Run + Cloud SQL for PostgreSQL，改善非洲地区访问延迟。

**Architecture:** Spring Boot 容器部署到 Cloud Run（africa-south1 区域），数据库迁移到 Cloud SQL for PostgreSQL，通过 Cloud SQL Java Connector（Unix socket）连接，无需 Auth Proxy。

**Tech Stack:** Google Cloud Run, Cloud SQL for PostgreSQL, Artifact Registry, gcloud CLI, Docker

---

## 文件变动

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 添加 Cloud SQL JDBC Connector 依赖 |
| `Dockerfile` | 无需修改 | 已经是多阶段构建，可直接复用 |
| `src/main/resources/application.yml` | 无需修改 | 数据库连接参数全部通过环境变量传入 |

---

## 前置条件（手动完成，约 10 分钟）

- [ ] **前置 1：注册 Google Cloud 账号**
  - 访问 https://cloud.google.com → 点击"免费开始使用"
  - 需要绑定信用卡（新用户有 $300 免费额度，不会自动扣款）

- [ ] **前置 2：安装 gcloud CLI**
  - 下载地址：https://cloud.google.com/sdk/docs/install#windows
  - 下载后运行安装程序，默认选项即可
  - 安装完成后打开新终端，运行：
    ```
    gcloud --version
    ```
    期望输出：`Google Cloud SDK 4xx.x.x`

- [ ] **前置 3：安装 Docker Desktop**
  - 若已安装可跳过
  - 下载地址：https://www.docker.com/products/docker-desktop/
  - 安装后启动 Docker Desktop，等待状态变为 "Running"

---

## Task 1：Google Cloud 项目初始化

**Files:** 无代码文件，纯 CLI 操作

- [ ] **Step 1：登录 gcloud**

  ```powershell
  gcloud auth login
  ```
  浏览器会弹出授权页面，用 Google 账号登录。

- [ ] **Step 2：创建项目（选择一个短名称，全球唯一）**

  ```powershell
  gcloud projects create warehouse-mgmt-2026 --name="Warehouse Management"
  gcloud config set project warehouse-mgmt-2026
  ```
  > 如果 `warehouse-mgmt-2026` 已被占用，换一个名字，如 `wh-mgmt-你的名字缩写-2026`
  
  期望输出：`Updated property [core/project].`

- [ ] **Step 3：开启计费（需要在控制台完成）**
  - 访问 https://console.cloud.google.com/billing
  - 将新项目关联到你的计费账号（有 $300 免费额度）

- [ ] **Step 4：启用所需 API**

  ```powershell
  gcloud services enable run.googleapis.com sqladmin.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com
  ```
  期望输出：等待约 30 秒，显示 `Operation finished successfully.`

- [ ] **Step 5：配置 gcloud 默认区域**

  ```powershell
  gcloud config set run/region africa-south1
  gcloud config set compute/region africa-south1
  ```

---

## Task 2：创建 Cloud SQL for PostgreSQL 实例

**Files:** 无代码文件，纯 CLI 操作

- [ ] **Step 1：创建 Cloud SQL 实例**

  ```powershell
  gcloud sql instances create warehouse-db `
    --database-version=POSTGRES_15 `
    --tier=db-f1-micro `
    --region=africa-south1 `
    --storage-size=10GB `
    --storage-type=SSD `
    --no-backup
  ```
  > `db-f1-micro` 是最低配置（共享 vCPU，614MB 内存），约 $7-9/月。
  > 创建需要 3-5 分钟，等待 `STATUS: RUNNABLE` 出现。

- [ ] **Step 2：设置数据库密码**

  ```powershell
  gcloud sql users set-password postgres `
    --instance=warehouse-db `
    --password=WH_DB_Pass_2026!
  ```
  > 记住这个密码，后面要用到。也可以换成你自己的强密码。

- [ ] **Step 3：创建数据库**

  ```powershell
  gcloud sql databases create warehouse_db --instance=warehouse-db
  ```
  期望输出：`Created database [warehouse_db].`

- [ ] **Step 4：查询实例连接名（保存备用）**

  ```powershell
  gcloud sql instances describe warehouse-db --format="value(connectionName)"
  ```
  期望输出格式：`warehouse-mgmt-2026:africa-south1:warehouse-db`
  > **保存这个字符串**，下面多处要用到，记为 `CONNECTION_NAME`

---

## Task 3：修改 pom.xml 添加 Cloud SQL 连接器

**Files:**
- Modify: `pom.xml`（在 `<dependencies>` 中添加）

- [ ] **Step 1：在 pom.xml 的 `<dependencies>` 块末尾添加依赖**

  打开 [pom.xml](pom.xml)，在最后一个 `</dependency>` 之后、`</dependencies>` 之前添加：

  ```xml
  <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>postgres-socket-factory</artifactId>
      <version>1.20.0</version>
  </dependency>
  ```

- [ ] **Step 2：验证本地可以编译**

  ```powershell
  cd D:\AI\warehouse-backend
  mvn package -DskipTests -q
  ```
  期望输出：无报错，`target/warehouse-backend-1.0.0.jar` 生成成功。

- [ ] **Step 3：提交代码**

  ```powershell
  git add pom.xml
  git commit -m "feat: add Cloud SQL JDBC connector for GCP deployment"
  git push
  ```

---

## Task 4：创建 Artifact Registry 并推送 Docker 镜像

**Files:** 无代码文件，CLI + Docker 操作

- [ ] **Step 1：创建 Artifact Registry 仓库**

  ```powershell
  gcloud artifacts repositories create warehouse-repo `
    --repository-format=docker `
    --location=africa-south1 `
    --description="Warehouse backend images"
  ```

- [ ] **Step 2：配置 Docker 认证**

  ```powershell
  gcloud auth configure-docker africa-south1-docker.pkg.dev
  ```
  期望输出：`Docker configuration file updated.`

- [ ] **Step 3：构建 Docker 镜像**

  ```powershell
  cd D:\AI\warehouse-backend
  docker build -t africa-south1-docker.pkg.dev/warehouse-mgmt-2026/warehouse-repo/backend:latest .
  ```
  > 首次构建需要下载 Maven + JDK 基础镜像，约需 5-10 分钟。
  > 期望输出最后一行：`Successfully tagged africa-south1-docker.pkg.dev/...`

- [ ] **Step 4：推送镜像**

  ```powershell
  docker push africa-south1-docker.pkg.dev/warehouse-mgmt-2026/warehouse-repo/backend:latest
  ```
  期望输出：最后显示 `latest: digest: sha256:... size: ...`

---

## Task 5：部署 Cloud Run 服务

**Files:** 无代码文件，CLI 操作

- [ ] **Step 1：获取 Cloud Run 服务账号**

  ```powershell
  $PROJECT_NUMBER = gcloud projects describe warehouse-mgmt-2026 --format="value(projectNumber)"
  echo "Service Account: $PROJECT_NUMBER-compute@developer.gserviceaccount.com"
  ```

- [ ] **Step 2：授予 Cloud Run 访问 Cloud SQL 的权限**

  ```powershell
  gcloud projects add-iam-policy-binding warehouse-mgmt-2026 `
    --member="serviceAccount:$PROJECT_NUMBER-compute@developer.gserviceaccount.com" `
    --role="roles/cloudsql.client"
  ```

- [ ] **Step 3：部署到 Cloud Run**

  将下面的 `CONNECTION_NAME` 替换为 Task 2 Step 4 得到的连接名（格式 `项目:区域:实例`）：

  ```powershell
  gcloud run deploy warehouse-backend `
    --image=africa-south1-docker.pkg.dev/warehouse-mgmt-2026/warehouse-repo/backend:latest `
    --platform=managed `
    --region=africa-south1 `
    --allow-unauthenticated `
    --memory=512Mi `
    --cpu=1 `
    --min-instances=0 `
    --max-instances=3 `
    --add-cloudsql-instances=CONNECTION_NAME `
    --set-env-vars="SPRING_DATASOURCE_URL=jdbc:postgresql:///warehouse_db?cloudSqlInstance=CONNECTION_NAME&socketFactory=com.google.cloud.sql.postgres.SocketFactory,SPRING_DATASOURCE_USERNAME=postgres,SPRING_DATASOURCE_PASSWORD=WH_DB_Pass_2026!"
  ```
  
  > 替换示例：如果 CONNECTION_NAME 是 `warehouse-mgmt-2026:africa-south1:warehouse-db`，
  > 则 SPRING_DATASOURCE_URL 为：
  > `jdbc:postgresql:///warehouse_db?cloudSqlInstance=warehouse-mgmt-2026:africa-south1:warehouse-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory`

  期望输出：
  ```
  Service [warehouse-backend] revision [warehouse-backend-xxxxx] has been deployed and is serving 100 percent of traffic.
  Service URL: https://warehouse-backend-xxxxxxxxxx-oa.a.run.app
  ```
  > **保存 Service URL**，这是新的后端地址。

- [ ] **Step 4：验证后端正常启动**

  ```powershell
  curl https://warehouse-backend-xxxxxxxxxx-oa.a.run.app/api/auth/login `
    -X POST `
    -H "Content-Type: application/json" `
    -d '{"username":"admin","password":"admin123"}'
  ```
  期望输出：包含 `"token":` 的 JSON 响应，说明数据库初始化成功、登录正常。

---

## Task 6：更新前端 API 地址

**Files:** Vercel 控制台操作（不需要改代码）

- [ ] **Step 1：更新 Vercel 环境变量**
  - 访问 https://vercel.com → 打开 warehouse-frontend 项目
  - Settings → Environment Variables
  - 找到 `VITE_API_BASE_URL`（或类似名称），更新值为：
    ```
    https://warehouse-backend-xxxxxxxxxx-oa.a.run.app/api
    ```
    > 末尾加 `/api`，对应 Spring Boot 的 `context-path: /api`

- [ ] **Step 2：重新部署前端**
  - Vercel → Deployments → 点击最新部署旁的 `...` → Redeploy
  - 等待部署完成（约 1-2 分钟）

- [ ] **Step 3：验证前端可以正常登录**
  - 打开 https://warehouse-frontend-rose.vercel.app
  - 使用 admin / admin123 登录
  - 确认可以看到仓库数据、分类等模块正常加载

---

## Task 7：迁移 Railway 现有数据（可选）

> 如果 Railway 上没有重要业务数据（只有初始数据），可以跳过此 Task。
> Cloud Run 首次启动时 `sql.init.mode: always` 会自动重新创建表和初始数据。

- [ ] **Step 1：从 Railway 导出数据**

  在 Railway 控制台找到 PostgreSQL 服务 → Connect → 复制 `DATABASE_URL`，然后运行：

  ```powershell
  pg_dump "DATABASE_URL" --no-owner --no-acl -f railway_backup.sql
  ```
  > 需要本地安装 PostgreSQL 客户端工具（`pg_dump`）

- [ ] **Step 2：导入到 Cloud SQL**

  先启动 Cloud SQL Auth Proxy（一次性操作，用于本地连接 Cloud SQL）：

  ```powershell
  # 下载 Cloud SQL Auth Proxy
  Invoke-WebRequest -Uri "https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.11.0/cloud-sql-proxy.windows.amd64.exe" -OutFile cloud-sql-proxy.exe

  # 启动（保持此窗口打开）
  .\cloud-sql-proxy.exe warehouse-mgmt-2026:africa-south1:warehouse-db --port=5433
  ```

  在另一个终端窗口导入数据：

  ```powershell
  psql "host=127.0.0.1 port=5433 dbname=warehouse_db user=postgres password=WH_DB_Pass_2026!" -f railway_backup.sql
  ```

---

## 费用估算

| 服务 | 规格 | 估算月费 |
|------|------|---------|
| Cloud Run | min=0 实例，按请求计费 | $0-2（轻量使用） |
| Cloud SQL db-f1-micro | africa-south1，10GB SSD | ~$8-10 |
| Artifact Registry | ~0.5GB 镜像存储 | ~$0.05 |
| **合计** | | **约 $8-12/月** |

> 新用户 $300 免费额度约可支撑 2-3 年。

---

## 回滚方案

如果 Cloud Run 出现问题，前端只需在 Vercel 将 `VITE_API_BASE_URL` 改回 Railway 的原 URL，即可立即回滚，Railway 服务在确认 Cloud Run 稳定前不要删除。
