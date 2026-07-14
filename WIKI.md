# AI Agent Demo 项目 Wiki

---

## 一、项目概述

### 1.1 项目定位

本项目是一个基于 **Spring AI** 构建的智能 Agent 系统，实现了完整的 AI 助手能力栈：

- **知识库检索（RAG）**：支持多策略召回和 LLM 重排序
- **工具调用（Function Calling）**：内置天气、股票等实用工具
- **技能执行（Skill）**：可扩展的技能系统
- **MCP 协议连接**：支持 Model Context Protocol 标准协议（双向 Client + Server）
- **子代理（SubAgent）**：独立上下文的子任务处理
- **多 Agent 编排**：任务分解 + 串行/并行执行 + 结果汇总
- **DAG 工作流引擎**：SpEL 条件边 + 5 种节点类型
- **安全护栏**：输入/输出双重防护 + 危险操作二次确认
- **弹性容错**：熔断器 + 降级处理 + 指数退避重试

### 1.2 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.0.5 |
| AI SDK | Spring AI | 2.0.0-M4 |
| 语言 | Java | 21 |
| JSON处理 | FastJSON | 1.2.83 |
| 缓存 | Caffeine | 3.1.8 |
| MCP 协议 | Spring AI MCP | Client + Server |

### 1.3 核心特性

- **流式响应**：支持 Server-Sent Events（SSE）实时推送
- **多模型兼容**：通过 OpenAI 兼容接口支持 ZhipuAI、DeepSeek、Moonshot 等
- **运行时切换模型**：无需重启即可切换底层 LLM
- **意图识别**：自动判断是否需要检索知识库
- **记忆管理**：三层压缩（摘要压缩 + Assistant 裁剪 + 滑动窗口）
- **容错机制**：熔断器（三态状态机）+ 分场景降级
- **成本控制**：Token 追踪 + 每日配额限制
- **链路追踪**：TraceContext + TraceSpan 完整调用链

---

## 二、架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP API Layer                           │
│  ChatController │ CommandController │ ManagerController         │
│  SkillController │ ActionController │ DebugController          │
├─────────────────────────────────────────────────────────────────┤
│                        Core Layer                               │
│  ┌─────────────┐  ┌───────────────┐  ┌──────────────────────┐  │
│  │  AgentCore  │→│IntentRecognizer│→│     ChatMemory       │  │
│  └──────┬──────┘  └───────────────┘  └──────────┬───────────┘  │
│         │                                       │               │
│         ▼                                       ▼               │
│  ┌─────────────┐  ┌───────────────┐  ┌──────────────────────┐  │
│  │ ReActExecutor│→│SubAgentManager│→│  AgentOrchestrator   │  │
│  └──────┬──────┘  └──────┬────────┘  └──────────────────────┘  │
│         │                │                                      │
│         ▼                ▼                                      │
│  ┌──────────────────────────────────┐  ┌─────────────────────┐  │
│  │       Tool Callback Registry    │  │  WorkflowEngine     │  │
│  │ RagTool|SubAgentTool|SkillTool  │  │  (DAG 执行引擎)      │  │
│  └──────────────────────────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                     Security & Resilience                       │
│  InputGuardrail │ OutputGuardrail │ CircuitBreaker │ Quota     │
├─────────────────────────────────────────────────────────────────┤
│                        Service Layer                            │
│  ┌──────────┐  ┌────────────┐  ┌─────────────┐  ┌──────────┐  │
│  │RagService│→│ VectorStore│→│  Retrievers │→│ Reranker │  │
│  └──────────┘  └────────────┘  └─────────────┘  └──────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │            MCP Client + MCP Server                       │  │
│  │  (Streamable HTTP + SSE 双协议自动检测)                    │  │
│  └──────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                      External Services                          │
│         LLM API | Embedding API | Rerank API                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责划分

| 模块 | 职责 | 核心类 |
|------|------|--------|
| api/controller | REST API 控制层 | ChatController, CommandController, ManagerController |
| api/exception | 异常处理 | GlobalExceptionHandler, ErrorCode |
| core | 核心业务逻辑 | AgentCore, IntentRecognizer, ChatMemory |
| core/agent | 多Agent编排 | AgentOrchestrator, TaskDecomposer, ResultAggregator |
| core/react | ReAct推理引擎 | ReActExecutor, ReActPromptTemplate |
| core/workflow | DAG工作流引擎 | WorkflowEngine, WorkflowNode, WorkflowEdge |
| core/resilience | 容错机制 | CircuitBreaker, FallbackHandler |
| core/guardrail | 安全护栏 | InputGuardrail, OutputGuardrail |
| core/action | 危险操作管理 | DangerousActionDetector, ActionPendingStore |
| core/cost | 成本控制 | QuotaManager, TokenUsageTracker |
| core/trace | 链路追踪 | TraceContext, TraceSpan, TraceRecorder |
| service/rag | RAG服务 | RagService, VectorStore, ChunkSplitter |
| service/skill | 技能系统 | SkillManager |
| service/command | 命令系统 | CommandManager |
| service/tool | 工具服务 | InnerTool, RagTool, SubAgentTool |
| service/external | MCP外部服务 | McpClient, McpHealthChecker, McpReconnectManager |

---

## 三、核心组件详解

### 3.1 AgentCore（核心编排器）

**定位**：Agent 系统的核心入口，负责协调所有组件完成对话处理。

**处理流程**：
```
用户输入 → 意图识别 → RAG增强（可选）→ 构建Prompt → 工具绑定 → 流式调用LLM → 记忆闭环
```

**设计要点**：
- **会话隔离**：通过 ConcurrentHashMap<String, ChatMemory> 实现多用户并发
- **工具自动注册**：启动时扫描所有 InnerTool Bean 并加载 ToolCallback
- **记忆闭环**：响应完成后自动保存到上下文，确保多轮对话连贯性
- **模型热切换**：运行时动态切换底层模型 API
- **参数动态调整**：temperature、maxTokens、topP 无需重启即可修改

### 3.2 IntentRecognizer（意图识别器）

**定位**：判断用户输入是否需要检索知识库。

**识别策略**：使用 few-shot prompt 引导 LLM 输出意图标签（RAG / GENERAL）。

**边界处理**：
- 识别异常时降级为 GENERAL
- 通过关键词匹配辅助判断（可扩展）

### 3.3 ChatMemory（对话记忆）

**定位**：管理会话上下文，支持三层压缩策略。

**三层压缩**：

| 层级 | 策略 | 触发条件 |
|------|------|----------|
| 第1层 | 摘要压缩 | 消息数 > 15 条 |
| 第2层 | Assistant 裁剪 | 每次获取消息时 |
| 第3层 | 滑动窗口 | 消息数 > maxRounds×4 |

**配置参数**：

| 特性 | 配置参数 | 默认值 |
|------|----------|--------|
| 最大轮数限制 | memory.max-rounds | 20 |
| 助手消息限制 | memory.max-assistant-messages | 3 |
| 自动压缩阈值 | memory.compression-threshold | 15 |
| 会话过期 | memory.expire-minutes | 1440 |

### 3.4 RagService（RAG服务）

**定位**：实现完整的 RAG 流程，支持多策略召回和 LLM 重排序。

**RAG 处理流程**：
```
知识库加载 → 文本分块(500字符/块) → 向量化存储 → 多路召回(9候选) → LLM重排序 → Top-3 结果拼接
```

**核心组件**：

| 组件 | 职责 | 实现类 |
|------|------|--------|
| **ChunkSplitter** | 文本分块 | TextSplitter (500字符/块，50字符重叠) |
| **VectorStore** | 向量存储 | 基于 Caffeine 的内存存储 |
| **Retriever** | 召回策略 | SemanticRetriever, Bm25Retriever, QueryRewriteRetriever |
| **MultiRetriever** | 多路召回聚合 | RRF（Reciprocal Rank Fusion）算法 |
| **LlmReranker** | LLM 重排序 | 调用独立 rerank API |

### 3.5 ReActExecutor（ReAct执行引擎）

**定位**：实现 ReAct（Reasoning + Action）推理框架，支持多轮工具调用。

**容错机制**：
- 最大迭代次数：10次
- 工具调用重试：3次（线性退避 1s/2s/3s）
- JSON 解析失败降级为直接回答
- 异常捕获：返回友好错误信息

### 3.6 SubAgent（子代理）

**定位**：执行时动态创建的轻量级 Agent，具有独立上下文。

**适用场景**：
- 需要独立上下文的复杂子任务（如代码审查、数据分析）
- 需要多轮交互的专项任务
- 避免污染主对话上下文

### 3.7 多 Agent 编排（AgentOrchestrator）

**编排流程**：
```
用户输入 → TaskDecomposer(LLM分解为2-5个子任务) → 
├── execute()         串行执行每个子任务
└── executeParallel() 线程池并发（最多5线程，60s超时）
→ ResultAggregator(LLM汇总结果) → 最终答案
```

### 3.8 DAG 工作流引擎（WorkflowEngine）

**节点类型**：StartNode、LlmNode、ConditionNode、ToolNode、EndNode

**特性**：
- SpEL 表达式作为条件边
- 循环检测（HashSet visited）
- 模板变量渲染 `{{variable}}`

---

## 四、工具系统

### 4.1 工具架构

```
InnerTool（接口）
├── RagTool          → knowledge_search
├── SubAgentTool     → create/chat/destroy_sub_agent
├── SkillMcpTool     → 动态技能工具
├── SubAgentMcpTool  → SubAgent MCP 工具
├── GetWeatherTool   → get_weather
└── GetStockPriceTool → get_stock_price
```

### 4.2 内置工具列表

| 工具名 | 所属类 | 功能描述 |
|--------|--------|----------|
| knowledge_search | RagTool | 知识库检索 |
| create_sub_agent | SubAgentTool | 创建子代理 |
| chat_with_sub_agent | SubAgentTool | 与子代理对话 |
| destroy_sub_agent | SubAgentTool | 销毁子代理 |
| get_weather | GetWeatherTool | 查询天气 |
| get_stock_price | GetStockPriceTool | 查询股票 |
| [动态技能] | SkillMcpTool | 根据加载的 Skill 自动生成 |
| [MCP远程工具] | McpClient | 通过 MCP 协议连接的远程工具 |

### 4.3 工具注册机制

- **启动时**：自动扫描所有 InnerTool Bean，调用 `loadToolCallbacks()` 注册
- **运行时**：MCP 远程工具通过 `connect()` 动态注册，`disconnect()` 动态移除
- **日志代理**：MCP 工具自动包装日志（工具名、入参、耗时、结果）

---

## 五、API 接口

### 5.1 对话接口（/api/chat）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 非流式对话 |
| POST | `/api/chat/stream` | 流式对话（SSE） |

### 5.2 管理接口（/api/manage）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/manage/clear-memory` | 清空会话历史 |
| POST | `/api/manage/switch-model` | 运行时切换模型 |
| GET | `/api/manage/model-params` | 获取模型参数 |
| POST | `/api/manage/model-params` | 更新模型参数 |
| POST | `/api/manage/mcp/connect` | 连接 MCP 服务 |
| POST | `/api/manage/mcp/disconnect` | 断开 MCP 服务 |
| GET | `/api/manage/mcp/list` | 获取 MCP 服务列表 |

### 5.3 命令接口（/api/command）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/command/list` | 获取命令列表 |
| POST | `/api/command/execute` | 执行命令（非流式） |
| POST | `/api/command/execute/stream` | 执行命令（流式） |

### 5.4 技能接口（/api/skill）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skill/list` | 获取技能列表 |
| POST | `/api/skill/upload` | 上传技能文件 |
| POST | `/api/skill/reload` | 重载所有技能 |
| POST | `/api/skill/remove` | 移除技能 |

### 5.5 操作确认接口（/api/action）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/action/confirm` | 确认危险操作 |
| POST | `/api/action/reject` | 拒绝危险操作 |
| GET | `/api/action/pending` | 获取待确认操作 |

### 5.6 调试接口（/api/debug）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/debug/status` | 系统状态 |
| GET | `/api/debug/trace` | 链路追踪 |

---

## 六、安全与容错

### 6.1 安全护栏

| 层级 | 组件 | 功能 |
|------|------|------|
| 输入 | InputGuardrail | Prompt 注入检测、敏感内容过滤、长度限制（5000字符） |
| 输出 | OutputGuardrail | PII 脱敏（身份证/银行卡/手机号/邮箱）、有害内容拦截 |
| 操作 | DangerousActionDetector | 危险操作检测（32个关键词）+ 二次确认机制 |

### 6.2 容错机制

| 组件 | 机制 | 参数 |
|------|------|------|
| CircuitBreaker | 三态熔断器（CLOSED→OPEN→HALF_OPEN） | 5次失败触发，30s恢复 |
| FallbackHandler | 分场景降级（模型/工具/RAG/MCP） | 返回友好提示 |
| ReActExecutor | 工具调用重试 | 3次，线性退避 |
| McpReconnectManager | MCP 断线重连 | 指数退避 [1,2,4,8,16]s |

### 6.3 成本控制

| 组件 | 功能 |
|------|------|
| QuotaManager | 每日配额限制（100,000 tokens/会话） |
| TokenUsageTracker | 按会话×模型维度追踪 Token 用量 |

---

## 七、配置说明

### 7.1 主要配置项

```properties
# LLM 配置
spring.ai.openai.base-url=https://open.bigmodel.cn/api/paas/v4
spring.ai.openai.api-key=your-key
spring.ai.openai.chat.options.model=glm-4.6
spring.ai.openai.embedding.options.model=embedding-3

# Agent 配置
agent.temperature=0.7
agent.max-tokens=2048
agent.max-iterations=10
agent.top-p=1.0

# 多 Agent 编排
agent.orchestration.enabled=true
agent.orchestration.mode=hierarchical
agent.orchestration.max-agents=10
agent.orchestration.timeout-seconds=300

# 记忆配置
memory.max-rounds=20
memory.compression-threshold=15
memory.expire-minutes=1440

# RAG 配置
rag.chunk-size=500
rag.chunk-overlap=50
rag.top-k=3
rag.recall-count=9
rag.rerank.path=/rerank
rag.rerank.model=rerank

# MCP Server
spring.ai.mcp.server.name=AiAgentDemo-MCP-Server
spring.ai.mcp.server.version=1.0.0
```

---

## 八、部署与运行

### 8.1 环境要求

- JDK 21+
- Maven 3.8+
- 可用的 LLM API Key（ZhipuAI / DeepSeek / Moonshot 等 OpenAI 兼容服务）

### 8.2 启动方式

```bash
cd E:\aiagentdemo-20260713
mvn spring-boot:run
```

### 8.3 前端访问

内置静态页面：`http://localhost:8080/index.html`

---

**文档版本**：v2.0  
**生成日期**：2026-07-14  
**项目地址**：https://github.com/tang1157026291-coder/aiagentdemo
