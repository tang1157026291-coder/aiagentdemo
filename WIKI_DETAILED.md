# AI Agent Demo 项目 Wiki - 详细实现版

---

## 一、核心组件详解

### 1.1 AgentCore（核心编排器）

**定位**：Agent 系统的核心入口，负责协调所有组件完成对话处理。

**处理流程**：
用户输入 → 意图识别 → RAG增强 → 构建Prompt → 工具绑定 → 流式调用LLM → 记忆闭环

**设计要点**：
| 设计点 | 实现方式 |
|--------|----------|
| 会话隔离 | ConcurrentHashMap<String, ChatMemory> |
| 工具自动注册 | Spring Bean扫描 |
| 记忆闭环 | doOnComplete回调 |
| 流式响应 | Flux<String> + SSE |

### 1.2 ChatMemory（对话记忆）

**压缩机制**：当消息数超过15条时，调用LLM生成300字以内的摘要，保留最近5条消息。

---

## 二、RAG系统深度解析

### 2.1 多路召回策略

| 召回器 | 原理 | 特点 |
|--------|------|------|
| SemanticRetriever | 向量余弦相似度 | 理解语义 |
| Bm25Retriever | BM25算法 | 精确匹配 |
| QueryRewriteRetriever | LLM重写+语义召回 | 扩展覆盖面 |

### 2.2 BM25算法
公式：score(q,d) = Σ [IDF(ti) × TF(ti,d) × (k1+1)] / [TF(ti,d) + k1 × (1-b + b×|d|/avgdl)]
参数：k1=1.5, b=0.75

### 2.3 RRF融合算法
公式：score(d) = Σ [1 / (60 + rank_i)]

### 2.4 LlmReranker
使用专用Rerank模型精排，速度快、成本低、质量高。

---

## 三、ReAct推理引擎

**执行流程**：用户问题 → 构建Prompt → LLM决策 → 工具调用 → 观察结果 → 迭代/结束

**容错机制**：最大迭代10次，工具调用重试3次（指数退避）。

---

## 四、工具系统

**内置工具**：knowledge_search, create_sub_agent, chat_with_sub_agent, destroy_sub_agent, get_weather, get_stock_price

---

**文档版本**：v2.0
**生成日期**：2026-07-14
