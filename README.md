# smartDataQuerying

smartDataQuerying 是一个基于 SQLBot 产品思路实现的 MVP 智能问数系统。首版支持管理员接入 MySQL/PostgreSQL 数据源，维护表结构备注、业务术语和 SQL 示例，并通过 OpenAI 兼容模型把自然语言问题转换为安全的只读 SQL。

## 目录结构

- `backend`：Spring Boot 3 + Java 21 API 服务
- `frontend`：Vite + React + TypeScript 管理台和问数界面
- `docs`：开发文档和验收清单
- `SQLBot_analysis_plan.MD`：SQLBot 借鉴分析

## 快速启动

1. 准备系统库 MySQL，并创建数据库：

```sql
create database smart_data_querying character set utf8mb4 collate utf8mb4_unicode_ci;
```

2. 复制 `.env.example` 中的配置到运行环境，至少修改：

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
JWT_SECRET
ENCRYPTION_SECRET
LLM_API_KEY
```

3. 启动后端：

```bash
cd backend
mvn spring-boot:run
```

4. 启动前端：

```bash
cd frontend
npm install
npm run dev
```

5. 浏览器打开 `http://localhost:5173`。开发默认账号是 `admin` / `admin`，生产环境请使用 BCrypt hash 覆盖 `ADMIN_PASSWORD_HASH`。

## 一键启动

Windows 下可以双击：

```text
start-dev.bat
```

或者在项目根目录运行：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1
```

脚本会加载 `.env`，分别打开后端和前端开发服务窗口。后端要求 Java 21、Maven、MySQL 已经安装并可用。

## 已实现能力

- 单管理员 JWT 登录
- MySQL/PostgreSQL 数据源 CRUD、连接测试、表结构同步
- 表/字段备注、启用状态、敏感字段标记
- 业务术语和 SQL 示例管理
- 业务术语分类管理，默认支持通用、商机、项目、客户，也可自定义分类
- OpenAI 兼容 LLM 配置、连接测试和调用
- 自然语言问数主链路
- JSqlParser 只读 SQL 校验和 `LIMIT` 改写
- JDBC 查询执行和结果表格展示
- 查询审计记录
- Excel `.xlsx` 导入系统库并自动注册为可问数数据源

## 安全边界

- 只允许单条 `SELECT`
- 禁止多语句
- 禁止 DDL/DML
- 禁止访问未启用表
- 禁止查询敏感或禁用字段
- 查询自动追加或收紧 `LIMIT`
- 数据源密码和 LLM API Key 使用 AES-GCM 加密存储

## Excel 导入

管理员可以在“数据源”页面上传 Excel：

- 仅支持 `.xlsx`
- 只读取第一个 sheet
- 第一行作为表头
- 每次导入都会创建一张新表
- 导入表会自动出现在 `Excel Imports` 数据源下
- 字段名会按首行表头生成，反引号会被移除，超长表头会截断，重复表头会自动加后缀
- 单文件默认限制 20MB，最多 50,000 行

导入完成后，在“问数”页面选择 `Excel Imports` 数据源即可查询导入数据。

## 术语分类

术语库支持按分类维护业务口径：

- 默认分类：通用、商机、项目、客户
- 可直接输入自定义分类
- 问数时会先判断用户问题命中的分类，再优先使用该分类下的术语
- 直接命中的跨分类术语也会被补充进 prompt
