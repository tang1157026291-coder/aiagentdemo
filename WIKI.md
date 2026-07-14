# AI Agent Demo 项目 Wiki

---

## 一、项目概述

### 1.1 项目定位

本项目是一个基于 **Spring AI** 构建的智能 Agent 系统，实现了完整的 AI 助手能力栈：

- **知识库检索（RAG）**：支持多策略召回和 LLM 重排序
- **工具调用（Function Calling）**：内置天气、股票等实用工具
- **技能执行（Skill）**：可扩展的技能系统
- **MCP 协议连接**：支持 Model Context Protocol 标准协议
- **子代理（SubAgent）**：独立上下文的子任务处理

### 1.2 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.0.5 |
| AI SDK | Spring AI | 2.0.0-M4 |
| 语言 | Java | 21 |
| JSON处理 | FastJSON | 1.2.83 |
| 缓存 | Caffeine | 3.1.8 |

### 1.3 核心特性

- **流式响应**：支持 Server-Sent Events（SSE）实时推送
- **多模型兼容**：通过 OpenAI 兼容接口支持 ZhipuAI、DeepSeek、Moonshot 等
- **意图识别**：自动判断是否需要检索知识库
- **记忆管理**：支持上下文压缩和摘要生成
- **容错机制**：内置熔断和降级处理

---

## 二、架构设计

### 2.1 整体架构图

`
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP API Layer                           │
│  ChatController │ CommandController │ ManagerController         │
├─────────────────────────────────────────────────────────────────┤
│                        Core Layer                               │
│  ┌─────────────┐  ┌───────────────┐  ┌──────────────────────┐   │
│  │  AgentCore  │→│IntentRecognizer│→│     ChatMemory       │   │
│  └──────┬──────┘  └───────────────┘  └──────────┬───────────┘   │
│         │                                       │                │
│         ▼                                       ▼                │
│  ┌─────────────┐  ┌───────────────┐  ┌──────────────────────┐   │
│  │ ReActExecutor│→│SubAgentManager│→│    AgentOrchestrator │   │
│  └──────┬──────┘  └──────┬────────┘  └──────────────────────┘   │
│         │                │                                      │
│         ▼                ▼                                      │
│  ┌──────────────────────────────────┐                           │
│  │        Tool Callback Registry    │                           │
│  │  RagTool | SubAgentTool | McpTool│                           │
│  └──────────────────────────────────┘                           │
├─────────────────────────────────────────────────────────────────┤
│                        Service Layer                            │
│  ┌──────────┐  ┌────────────┐  ┌─────────────┐  ┌──────────┐   │
│  │ RagService│→│  VectorStore│→│   Retrievers│→│ Reranker │   │
│  └──────────┘  └────────────┘  └─────────────┘  └──────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    MCP Server                            │   │
│  │  (Spring AI MCP WebMVC Server)                          │   │
│  └──────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                      External Services                          │
│         LLM API | Embedding API | Rerank API                   │
└─────────────────────────────────────────────────────────────────┘
`

### 2.2 模块职责划分

| 模块 | 职责 | 核心类 |
|------|------|--------|
| pi/controller | REST API 控制层 | ChatController, CommandController |
| pi/dto | 数据传输对象 | ChatRequest, ChatResponse |
| pi/exception | 异常处理 | GlobalExceptionHandler, ErrorCode |
| core | 核心业务逻辑 | AgentCore, IntentRecognizer, ChatMemory |
| core/agent | 多Agent编排 | AgentOrchestrator, TaskDecomposer |
| core/react | ReAct推理引擎 | ReActExecutor, ReActPromptTemplate |
| core/workflow | 工作流引擎 | WorkflowEngine, WorkflowNode |
| core/resilience | 容错机制 | CircuitBreaker, FallbackHandler |
| service/rag | RAG服务 | RagService, VectorStore, ChunkSplitter |
| service/tool | 工具服务 | InnerTool, RagTool, SubAgentTool |
| service/external | 外部服务 | McpClient, McpHealthChecker |

---

## 三、核心组件详解

### 3.1 AgentCore（核心编排器）

**定位**：Agent 系统的核心入口，负责协调所有组件完成对话处理。

**处理流程**：
用户输入 → 意图识别 → RAG增强（可选）→ 构建Prompt → 工具绑定 → 流式调用LLM → 记忆闭环

**关键实现**（core/AgentCore.java）：

`java
// 1. 获取或创建会话记忆
ChatMemory memory = getOrCreateMemory(sessionId);

// 2. 意图识别（RAG/GENERAL）
Intent intent = intentRecognizer.recognize(userInput);

// 3. RAG知识增强（仅当意图为RAG且知识库已加载）
if (intent == Intent.RAG && ragService.isKnowledgeLoaded()) {
    String ragContext = ragService.query(userInput);
    String enrichedInput = " 以下是从知识库中检索到的相关参考资料...\;
 memory.addMessage(new UserMessage(enrichedInput));
}

// 4. 构建Prompt并绑定工具
List<Message> messages = memory.getMessages();
Prompt prompt = new Prompt(messages, buildChatOptions());

// 5. 流式调用LLM
return requestSpec.stream().content()
 .doOnNext(fullResponse::append)
 .doOnComplete(() -> memory.addMessage(new AssistantMessage(response)));
`

**设计要点**：
- **会话隔离**：通过 ConcurrentHashMap<String, ChatMemory> 实现多用户并发
- **工具自动注册**：启动时扫描所有 InnerTool Bean 并加载 ToolCallback
- **记忆闭环**：响应完成后自动保存到上下文，确保多轮对话连贯性

### 3.2 IntentRecognizer（意图识别器）

**定位**：判断用户输入是否需要检索知识库。

**识别策略**：使用 few-shot prompt 引导 LLM 输出意图标签（RAG / GENERAL）。

**边界处理**：
- 识别异常时降级为 GENERAL
- 通过关键词匹配辅助判断（可扩展）

### 3.3 ChatMemory（对话记忆）

**定位**：管理会话上下文，支持自动压缩和摘要生成。

**核心特性**：

| 特性 | 配置参数 |
|------|----------|
| 最大轮数限制 | memory.max-rounds=20 |
| 助手消息限制 | memory.max-assistant-messages=3 |
| 自动压缩阈值 | memory.compression-threshold=15 |
| 会话过期 | memory.expire-minutes=1440 |

**压缩机制**：当消息数超过阈值时，调用 LLM 将历史对话压缩为 300 字以内的摘要。

### 3.4 RagService（RAG服务）

**定位**：实现完整的 RAG 流程，支持多策略召回和 LLM 重排序。

**RAG 处理流程**：
知识库加载 → 文本分块 → 向量化存储 → 多策略召回 → LLM重排序 → 结果拼接

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
- 工具调用重试：3次（指数退避）
- 异常捕获：返回友好错误信息

### 3.6 SubAgent（子代理）

**定位**：执行时动态创建的轻量级 Agent，具有独立上下文。

**适用场景**：
- 需要独立上下文的复杂子任务（如代码审查、数据分析）
- 需要多轮交互的专项任务
- 避免污染主对话上下文

---

## 四、工具系统

### 4.1 工具架构

`
┌─────────────────┐
│ InnerTool │ ← 工具接口，所有工具必须实现
└────────┬────────┘
 │ 实现
 ┌────┴────┬────────────┬─────────────┐
 ▼ ▼ ▼ ▼
 RagTool SkillTool SubAgentTool McpTool
`

### 4.2 内置工具列表

| 工具名 | 所属类 | 功能描述 |
|--------|--------|----------|
| knowledge_search | RagTool | 知识库检索 |
| create_sub_agent | SubAgentTool | 创建子代理 |
| chat_with_sub_agent | SubAgentTool | 与子代理对话 |
| destroy_sub_agent | SubAgentTool | 销毁子代理 |
| get_weather | GetWeatherTool | 查询天气 |
| get_stock_price | GetStockPriceTool | 查询股票 |

---

## 五、API 接口

### 5.1 对话接口

**POST /api/chat** - 非流式对话

**POST /api/chat/stream** - 流式对话（SSE）

### 5.2 管理接口

**POST /api/manager/switch-model** - 切换模型

**POST /api/manager/clear-session** - 清除会话

---

## 六、配置说明

### 6.1 主要配置项

`properties
# LLM 配置
spring.ai.openai.base-url=https://open.bigmodel.cn/api/paas/v4
spring.ai.openai.chat.options.model=glm-4.6

# Agent 配置
agent.temperature=0.7
agent.max-tokens=2048
agent.max-iterations=10

# RAG 配置
rag.chunk-size=500
rag.chunk-overlap=50
rag.top-k=3
`

---

## 七、部署与运行

### 7.1 环境要求

- JDK 21+
- Maven 3.8+
- 可用的 LLM API Key

### 7.2 启动方式

`ash
cd E:\aiagentdemo-20260713
mvn spring-boot:run
`

---

**文档版本**：v1.0 
**生成日期**：2026-07-14 
**项目地址**：E:\aiagentdemo-20260713
