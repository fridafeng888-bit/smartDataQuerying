# SQLBot 项目分析与借鉴计划

本文档分析 DataEase 开源项目 SQLBot，并提炼可借鉴设计与 smartDataQuerying 的落地计划。

## 1. 项目定位

SQLBot 是一个基于大模型和 RAG 的 ChatBI/智能问数系统，目标是让用户通过自然语言完成 SQL 生成、数据查询、图表生成和进一步分析。它支持配置大模型、连接多类数据源、管理术语和 SQL 示例，并提供 Web 嵌入、浮窗助手、MCP 调用等集成方式。

它的产品边界比普通 Text-to-SQL demo 更完整：

- 对业务用户：自然语言问数、图表展示、推荐问题、历史记录。
- 对管理员：模型配置、数据源配置、权限、工作空间、术语库、SQL 示例。
- 对外部系统：JS Widget、iframe/页面嵌入、MCP Server、第三方数据源接入。
- 对运维：Docker 一键部署、PostgreSQL 内置、MCP 单独端口、日志和缓存配置。

## 2. 架构拆解

后端采用 FastAPI，模块按领域组织，如 chat、datasource、system、mcp、ai_model。启动时会执行数据库迁移、缓存初始化、动态 CORS、术语/训练数据/表与数据源 embedding 补全、模型信息加密等初始化动作。

技术栈要点：

- 后端：FastAPI、SQLModel/SQLAlchemy、Alembic、Pydantic Settings。
- LLM/RAG：LangChain、LangGraph、sentence-transformers、pgvector。
- 数据处理：pandas、openpyxl、python-calamine、xlsxwriter。
- 数据源：PostgreSQL、MySQL、SQL Server、Oracle、ClickHouse、Doris、Redshift、Elasticsearch、Excel 等。
- 集成：FastAPI-MCP、前端 `assistant.js` 浮窗/iframe 嵌入。
- 部署：单容器内编排 PostgreSQL、G2-SSR 图表渲染、MCP 服务和主 Web 服务。

值得注意的是，它把 MCP 服务挂在 `8001`，主 Web 服务挂在 `8000`，并通过 `FastApiMCP` 将部分 API 暴露为工具能力。这对“让智能问数成为其他 Agent/工作流平台可调用能力”很有参考价值。

## 3. 最值得借鉴的设计

### 3.1 围绕数据库语义构建 RAG

SQLBot 的 RAG 不是只检索文档，而是围绕数据库语义建知识层。它会把术语、SQL 示例、训练数据、表和数据源信息向量化，用于增强 SQL 生成。

智能问数的准确率通常不取决于模型有多聪明，而取决于它是否知道“GMV、有效用户、净收入、渠道、组织口径”这些业务语义。

可借鉴做法：

- 建业务术语库：指标、维度、同义词、计算口径。
- 建 SQL 示例库：自然语言问题、标准 SQL、适用数据源、适用表。
- 对表、字段、字段注释和样例值做 embedding。
- 查询时先召回相关业务语义，再生成 SQL。

### 3.2 模型供应商配置产品化

SQLBot 支持多个 OpenAI 兼容供应商，并有模型工厂、默认模型、参数配置、连接校验、密钥加密。

可借鉴做法：

- 不在代码中硬编码模型名、API Key、temperature。
- 提供模型供应商配置页面或配置文件。
- 支持默认模型、embedding 模型、SQL 生成模型分开配置。
- 对密钥进行加密存储。
- 在保存模型配置前做连接测试。

### 3.3 数据源抽象完整

SQLBot 支持多种数据库和 Excel，且数据源管理不只是连接字符串，还包含表、字段、字段类型、表关系、字段权限、连接测试、执行详情等能力。

可借鉴做法：

- 数据源配置和业务使用解耦。
- 将表结构同步为系统元数据。
- 对表/字段增加业务备注和可见性配置。
- SQL 生成只基于用户有权访问的数据源和字段。
- 查询执行记录进入审计日志。

### 3.4 嵌入式助手成熟

SQLBot 支持浮窗、全屏 iframe、PostMessage 通信、动态 CORS、Token 鉴权、外观配置、图标配置。

这说明它的目标不是只做一个独立后台，而是成为其他系统的“问数组件”。

可借鉴做法：

- 先做 iframe 嵌入，成本最低。
- 后续再做 JS Widget 浮窗。
- 嵌入访问必须配域名白名单和 Token 鉴权。
- 嵌入模式下支持隐藏导航、固定数据源、固定助手。

### 3.5 MCP 化很前瞻

SQLBot 通过 MCP 暴露数据源列表、模型列表、问答、助手调用、工作空间选择等能力，能让 Dify、n8n、MaxKB 或自研 Agent 直接调用 SQLBot。

对 smartDataQuerying 来说，这相当于把智能问数从“页面功能”升级成“平台能力”。

可借鉴 MCP 工具：

- `list_datasources`：列出用户可访问的数据源。
- `list_tables`：列出指定数据源下的表。
- `ask_question`：输入自然语言问题，返回 SQL、结果和解释。
- `run_readonly_sql`：执行经过安全校验的只读 SQL。
- `get_query_history`：读取历史问数记录。

### 3.6 安全意识值得重视

SQLBot 最新版本集中修复了权限提升、提示词注入等漏洞，前一个版本也修复了 SSRF、Excel 上传远程代码执行、存储型指令注入等问题。

这些漏洞类型基本就是智能问数系统的高风险区：用户输入、SQL 执行、文件上传、外部数据源、嵌入访问、Agent 工具调用。

必须借鉴的安全边界：

- 只读数据库账号。
- SQL AST 校验。
- 查询超时。
- 查询行数限制。
- 敏感字段脱敏。
- 上传文件隔离扫描。
- 外部 URL/数据源 SSRF 防护。
- Prompt 注入检测。
- MCP 工具级权限控制。
- 全链路审计日志。

## 4. 不建议直接照搬的地方

- 单容器把 PostgreSQL、后端、SSR、MCP 都放一起，部署简单，但生产环境可观测性、弹性扩容和隔离性一般。可以借鉴“一键启动体验”，生产部署建议拆服务。
- GPLv3 加 FIT2CLOUD 额外限制，二次开发或商用前要非常谨慎。可以学习架构和产品设计，但不建议直接拷贝代码进闭源项目。
- Text-to-SQL 的核心能力不能只依赖 prompt。必须配套 SQL 白名单/黑名单、只读连接、SQL AST 校验、超时、限流、行数限制、脱敏和审计。
- 企业版能力里可能有 X-Pack 依赖，开源部分能学到框架，但某些认证/权限/高级助手能力可能不是完全开放。

## 5. smartDataQuerying 落地路线

### 阶段 1：最小可用智能问数核心，1-2 周

目标：先跑通“自然语言 -> SQL -> 执行 -> 表格结果”。

交付物：

- 数据源连接管理，先支持 PostgreSQL/MySQL/SQLite 中的 1-2 种。
- 表结构同步：表、字段、类型、备注、样例值。
- LLM 配置抽象：支持 OpenAI 兼容 API，模型参数可配置。
- SQL 生成链路：用户问题 + 表结构上下文 + 业务术语 + SQL 示例。
- SQL 安全层：只允许 `SELECT`，禁止 DDL/DML，限制行数、超时、库表范围。
- 查询结果表格展示。

验收标准：

- 能连接至少一个真实业务库。
- 能同步表结构。
- 能完成 10 个典型自然语言问题的 SQL 生成。
- 所有生成 SQL 都经过只读校验。
- 查询失败时有清晰错误提示。

### 阶段 2：RAG 和业务知识层，2-4 周

目标：让系统“越用越准”。

交付物：

- 术语库：业务词、同义词、指标口径、适用数据源。
- SQL 示例库：问题、SQL、适用表、标签、启用状态。
- 表/字段 embedding：用于自动选择相关数据源和表。
- 查询失败反馈：把 SQL 错误返回给模型做一次修复。
- 用户反馈：正确/错误/收藏/改写 SQL，沉淀为训练样本。

验收标准：

- 新增术语后，相同问题能使用正确业务口径。
- SQL 示例能被召回并影响生成结果。
- 多表场景下能自动选择相关表。
- 对常见 SQL 错误能自动修复一次。

### 阶段 3：产品化问答体验，4-8 周

目标：从 demo 变成内部可用工具。

交付物：

- 聊天历史、会话管理、推荐问题。
- 图表建议：根据结果字段自动推荐表格、柱状图、折线图、饼图。
- 结果导出：CSV/Excel。
- 数据权限：工作空间、用户、数据源、表级/列级权限。
- 审计日志：谁问了什么、生成了什么 SQL、查了多少行、是否失败。

验收标准：

- 业务用户可以连续追问。
- 查询结果能一键转为图表。
- 管理员可以限制用户访问范围。
- 所有查询都有可追踪记录。

### 阶段 4：平台集成能力，8-12 周

目标：让智能问数可以嵌入现有业务系统。

交付物：

- iframe 嵌入模式。
- JS 浮窗助手。
- Token 鉴权和域名白名单。
- MCP Server：暴露 `list_datasources`、`ask_question`、`get_query_result`、`list_workspaces`。
- API Key 管理、调用限流、调用日志。

验收标准：

- 能嵌入一个现有系统页面。
- 外部系统能通过 MCP 或 API 发起问数。
- 嵌入访问不能越权访问数据。
- 调用日志可检索。

### 阶段 5：生产安全加固，持续

目标：避免智能问数系统变成数据泄漏入口。

必须项：

- SQL AST 解析和重写，不靠字符串判断。
- 只读数据库账号。
- 查询超时、结果行数、并发限制。
- 敏感字段脱敏。
- 文件上传隔离扫描。
- Prompt 注入检测。
- SSRF 防护。
- MCP 工具调用权限隔离。
- 全链路审计和异常告警。

验收标准：

- DDL/DML/危险函数无法执行。
- 用户只能查询自己授权的数据源、表和字段。
- 敏感字段默认不可出现在结果中。
- 所有外部调用都有身份、时间、参数和结果摘要记录。

## 6. 推荐优先级

最高优先级借鉴：

1. 术语库 + SQL 示例库 + 表结构 embedding。
2. 模型配置中心，而不是硬编码模型。
3. SQL 安全执行层。
4. 数据源/表/字段治理。
5. MCP 或 API 化，让问数能力可被外部系统调用。

可以后置：

1. 多租户复杂权限。
2. 多数据库全量支持。
3. 高级图表和仪表板。
4. 完整嵌入外观配置。
5. 企业认证集成。

## 7. 建议的技术实现清单

### 后端模块

- `datasource`：数据源连接、测试、表结构同步。
- `metadata`：表、字段、字段注释、样例值。
- `knowledge`：术语库、SQL 示例库、训练样本。
- `embedding`：向量化任务、召回接口。
- `chat`：自然语言问数链路。
- `sql_guard`：SQL AST 校验、重写、限流。
- `query`：执行查询、结果分页、导出。
- `audit`：查询日志、风险日志、调用日志。
- `integration`：iframe、Widget、API Key。
- `mcp`：MCP 工具暴露。

### 数据表建议

- `ai_model_provider`
- `ai_model`
- `datasource`
- `datasource_table`
- `datasource_column`
- `business_term`
- `sql_example`
- `embedding_job`
- `chat_session`
- `chat_message`
- `query_execution`
- `query_audit_log`
- `api_key`
- `mcp_tool_log`

### 核心链路建议

```text
用户问题
  -> 权限过滤
  -> 召回相关数据源/表/字段/术语/SQL 示例
  -> 生成候选 SQL
  -> SQL AST 安全校验
  -> SQL 改写：LIMIT、字段脱敏、库表约束
  -> 只读连接执行
  -> 结果归一化
  -> 图表建议
  -> 解释 SQL 和结果
  -> 写入审计日志
```

## 8. 风险清单

### 技术风险

- SQL 生成准确率不稳定。
- 多表 join 容易误选字段。
- 表结构太多时上下文过长。
- 向量召回质量依赖字段注释和术语质量。
- 不同数据库 SQL 方言差异大。

缓解措施：

- 先限定数据源范围和业务场景。
- 建 SQL 示例库。
- 强制管理员维护表/字段备注。
- 按数据源方言生成 SQL。
- 失败时进行一次自动修复，并保留人工反馈入口。

### 安全风险

- 越权查询。
- Prompt 注入。
- 危险 SQL 执行。
- 敏感数据泄漏。
- 上传文件攻击。
- MCP 工具被滥用。

缓解措施：

- 权限前置过滤。
- SQL AST 校验。
- 只读账号。
- 字段脱敏。
- 查询限流。
- MCP 工具级鉴权。
- 审计日志不可关闭。

### 产品风险

- 用户把智能问数当作绝对正确结果。
- 业务术语没人维护。
- 生成结果难以解释。
- 数据源接入后无人治理。

缓解措施：

- 明确展示生成 SQL 和使用字段。
- 结果页面显示口径来源。
- 提供“反馈错误”入口。
- 给管理员术语和 SQL 示例维护任务。

## 9. 参考来源

- [SQLBot GitHub 仓库](https://github.com/dataease/SQLBot)
- [SQLBot README](https://raw.githubusercontent.com/dataease/SQLBot/main/README.md)
- [SQLBot Releases](https://github.com/dataease/SQLBot/releases)
- [SQLBot DeepWiki 架构概览](https://deepwiki.com/dataease/SQLBot)
- [SQLBot Backend Architecture](https://deepwiki.com/dataease/SQLBot/6.1-backend-architecture)
- [SQLBot Integration Guide](https://deepwiki.com/dataease/SQLBot/4-integration-guide)
- [SQLBot AI Model Configuration](https://deepwiki.com/dataease/SQLBot/5.2-ai-model-configuration)
- [SQLBot Getting Started](https://deepwiki.com/dataease/SQLBot/2-getting-started)

