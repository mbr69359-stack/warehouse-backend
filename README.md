# 仓储管理系统 — 后端

基于 Spring Boot 2.7 + PostgreSQL 的仓储管理系统后端，提供 RESTful API，支持入库、出库、库存、供应商、用户权限等管理功能。

## 技术栈

- Java 8 / Spring Boot 2.7
- Spring Security + JWT 认证
- MyBatis-Plus
- PostgreSQL
- Knife4j（API 文档）

## 功能模块

- 用户管理 & 角色权限（RBAC）
- 仓库管理
- 商品 & 分类管理
- 供应商管理
- 入库单 / 出库单
- 库存查询 & 盘点
- 操作日志

## 本地运行

### 前置条件

- JDK 8+
- Maven 3.6+
- PostgreSQL 12+

### 步骤

1. 克隆仓库

```bash
git clone https://github.com/your-username/warehouse-backend.git
cd warehouse-backend
```

2. 创建数据库

```sql
CREATE DATABASE warehouse_db;
```

3. 配置环境变量（复制示例文件）

```bash
cp .env.example .env
# 编辑 .env，填写数据库连接信息和 JWT 密钥
```

4. 启动项目

```bash
mvn spring-boot:run
```

启动后访问 API 文档：http://localhost:8080/api/doc.html

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |

## 环境变量说明

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `SPRING_DATASOURCE_URL` | PostgreSQL 连接 URL | `jdbc:postgresql://localhost:5432/warehouse_db` |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | `your_password` |
| `JWT_SECRET` | JWT 签名密钥（256位以上随机字符串） | `your-secret-key` |

## Docker 部署

```bash
docker build -t warehouse-backend .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/warehouse_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=your_password \
  -e JWT_SECRET=your-secret-key \
  warehouse-backend
```

## 部署到 Google Cloud Run

参考 [迁移计划文档](docs/superpowers/plans/2026-06-01-railway-to-gcp-migration.md)

## License

MIT
