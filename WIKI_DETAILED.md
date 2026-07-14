# AI Agent Demo 详细技术文档

---

## 一、核心组件详解

### 1.1 AgentCore（核心编排器）

**定位**：Agent 系统的核心入口，负责协调所有组件完成对话处理。

**完整处理流程**：

```
用户输入 → 获取会话记忆 → 意图识别 → RAG知识增强(可选) → 构建Prompt → 工具绑定 → 流式/同步调用LLM → 记忆闭环
```

**设计要点**：

| 设计点 | 实现方式 | 说明 |
|--------|----------|------|
| 会话隔离 | `ConcurrentHashMap<String, ChatMemory>` | 支持多客户端并发，按 sessionId 隔离 |
| 工具自动注册 | `ApplicationContext.getBeansOfType(InnerTool.class)` | 启动时扫描所有 InnerTool Bean |
| 记忆闭环 | `doOnComplete()` 回调 | 流式输出完成后自动保存 AssistantMessage |
| 流式响应 | `Flux<String>` + SSE | 支持前端打字机效果 |
| 模型热切换 | `switchModel(ModelConfig)` | 运行时动态切换底层模型 API |
| 参数动态调整 | `setModelParams(temperature, maxTokens, topP)` | 无需重启即可调整推理参数 |

**流式对话核心代码逻辑**：

```java
// 阶段0: 获取会话记忆
ChatMemory memory = getOrCreateMemory(sessionId);

// 阶段1: 意图识别
Intent intent = intentRecognizer.recognize(userInput);

// 阶段2: RAG 知识增强（条件触发）
if (intent == Intent.RAG && ragService.isKnowledgeLoaded()) {
    String ragContext = ragService.query(userInput);
    // 将知识库内容注入用户问题前
    memory.addMessage(new UserMessage(enrichedInput));
}

// 阶段3: 构建 Prompt + 绑定工具
List<Message> messages = memory.getMessages(); // 内部自动触发摘要压缩
Prompt prompt = new Prompt(messages, buildChatOptions());
requestSpec.toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));

// 阶段4: 流式调用 + 记忆闭环
return requestSpec.stream().content()
    .doOnNext(fullResponse::append)
    .doOnComplete(() -> memory.addMessage(new AssistantMessage(response)))
    .doOnError(error -> memory.addMessage(new AssistantMessage("")));
```

### 1.2 ChatMemory（对话记忆管理器）

**定位**：管理会话上下文，支持三层压缩策略防止上下文超出模型限制。

**三层压缩策略**：

| 层级 | 策略 | 触发条件 | 实现 |
|------|------|----------|------|
| 第1层 | 摘要压缩 | 消息数 > 15 条 | 调用 LLM 生成 ≤300 字摘要，保留最近 5 条 |
| 第2层 | Assistant 裁剪 | 每次获取消息时 | 仅保留最近 3 条 Assistant 回复 |
| 第3层 | 滑动窗口 | 消息数 > maxRounds×4 | 移除最早的消息（保护 TOOL 消息完整性） |

**主 Agent vs SubAgent 差异**：

| 参数 | 主 Agent | SubAgent |
|------|----------|----------|
| 最大历史轮数 | 20 | 10 |
| 最多 Assistant 回复 | 3 | 3 |
| 摘要压缩 | ✅ 启用 | ❌ 不启用 |
| ChatClient | 共享主 ChatClient | 无独立 ChatClient |

**摘要压缩器（SummaryCompressor）**：

- 调用 LLM 总结历史对话为简洁摘要
- 保留关键信息：用户核心需求、重要决策、已完成操作、关键结论
- 去除冗余：寒暄、重复内容、中间推理过程
- 使用第三人称描述
- 支持增量摘要：新摘要与旧摘要合并
- 容错：压缩失败时保留原有摘要不丢失

**TOOL 消息保护机制**：
- 滑动窗口裁剪时不会在 TOOL 消息前截断
- 摘要压缩时确保不破坏 ASSISTANT→TOOL 的配对关系

### 1.3 IntentRecognizer（意图识别器）

**定位**：判断用户输入是否需要检索知识库。

**识别策略**：使用 few-shot prompt 引导 LLM 输出意图标签。

**意图类型**：

| 意图 | 说明 | 示例 |
|------|------|------|
| RAG | 需要知识库检索 | "查一下 HashMap 的原理" |
| GENERAL | 普通对话或工具调用 | "今天天气怎么样" |

**边界处理**：
- 识别异常时降级为 GENERAL（不影响主流程）
- 通过关键词匹配辅助判断（可扩展）

### 1.4 SessionManager（会话管理器）

**定位**：基于 Caffeine 缓存管理会话生命周期。

**特性**：
- 会话自动过期：默认 1440 分钟（24 小时）
- 线程安全：ConcurrentHashMap + Caffeine Cache
- 支持按 sessionId 隔离不同客户端

---

## 二、RAG 系统深度解析

### 2.1 完整 RAG 流程

```
知识库加载 → 文本分块(500字符/块, 50字符重叠) → 向量化存储 → 多路召回(9候选) → LLM重排序 → Top-K(3) 结果拼接
```

### 2.2 多路召回策略

| 召回器 | 类名 | 原理 | 特点 |
|--------|------|------|------|
| 语义召回 | `SemanticRetriever` | 向量余弦相似度 | 理解语义，适合近义词/同义表述 |
| BM25 召回 | `Bm25Retriever` | BM25 统计算法 | 精确关键词匹配，不依赖 Embedding |
| 查询改写召回 | `QueryRewriteRetriever` | LLM 重写查询 + 语义召回 | 扩展覆盖面，解决表述差异问题 |

### 2.3 BM25 算法实现

**公式**：
```
score(q, d) = Σ [IDF(ti) × TF(ti,d) × (k1+1)] / [TF(ti,d) + k1 × (1-b + b×|d|/avgdl)]
```

**参数配置**：
- k1 = 1.5（词频饱和参数）
- b = 0.75（文档长度归一化参数）

**实现细节**（`Bm25Retriever`，143行）：
- 自建倒排索引，纯 Java 实现，不依赖外部搜索引擎
- 支持中文分词（基于 Unicode 字符边界）
- IDF 计算基于文档集合统计

### 2.4 RRF 融合算法

**公式**：
```
score(d) = Σ [1 / (60 + rank_i)]
```

**说明**：
- `rank_i` 为文档在第 i 路召回中的排名
- 常数 60 用于平滑排名差异
- 自动去重：相同文档仅保留最高融合分

### 2.5 LLM Reranker（精排器）

**定位**：使用专用 Rerank 模型对召回结果精排。

**实现**（`LlmReranker`，174行）：
- 调用独立的 Rerank API（非通用 Chat API）
- 配置独立的 API 路径：`rag.rerank.path=/rerank`
- 配置独立的模型：`rag.rerank.model=rerank`
- 输入：(query, document) 对
- 输出：relevance_score 分数

**优势**：
- 速度快（专用小模型）
- 成本低（远低于 Chat 模型）
- 质量高（专门训练的交叉编码器）

### 2.6 文本分块策略

项目实现了 **8 种分块策略**：

| 分类 | 策略 | 说明 |
|------|------|------|
| 确定性 | 固定大小分块 | 按字符数固定切割 |
| 确定性 | 重叠分块 | 相邻块有重叠区域 |
| 确定性 | 段落分块 | 按段落标记分割 |
| 确定性 | 递归分块（TextSplitter） | 按分隔符层次递归切割，225行实现 |
| 智能 | 语义分块（SemanticChunkSplitter） | 基于语义相似度动态切割，256行 |
| 智能 | 命题分块（PropositionSplitter） | LLM 提取独立命题 |
| 智能 | Agentic 分块（AgenticSplitter） | Agent 自主决策切割点 |
| 递归 | 递归语义分块 | 结合递归 + 语义的混合策略 |

**默认配置**：
- 分块大小：500 字符
- 重叠区域：50 字符

### 2.7 向量存储（VectorStore）

- 基于 Caffeine 缓存的内存向量存储
- 支持余弦相似度计算
- 内置 EmbeddingCache 避免重复 Embedding 调用

---

## 三、ReAct 推理引擎

### 3.1 执行流程

```
用户问题 → 构建 System Prompt(含工具描述) → LLM 决策 → 解析 Action → 
├── action == "finish" → 返回最终答案
└── action == 工具名 → 执行工具(含重试) → 获取 Observation → 继续迭代
```

### 3.2 核心参数

| 参数 | 值 | 说明 |
|------|------|------|
| MAX_ITERATIONS | 10 | 最大推理迭代次数 |
| RETRY_ATTEMPTS | 3 | 工具调用重试次数 |
| 退避策略 | `Thread.sleep(1000L * attempt)` | 线性退避（1s, 2s, 3s） |

### 3.3 ReAct Decision 格式

LLM 输出 JSON 格式的决策：

```json
{
  "thought": "思考过程",
  "action": "工具名称 或 finish",
  "actionInput": "工具参数 或 最终答案",
  "isFinal": false
}
```

### 3.4 容错机制

- **解析失败降级**：JSON 解析失败时，将原始响应作为最终答案返回
- **工具不存在**：抛出 `AgentException(TOOL_ERROR)`
- **重试耗尽**：返回 `[工具调用失败] 达到最大重试次数`
- **全局异常**：返回 `ReActResult.success=false`，包含错误信息和已执行步骤

### 3.5 ReActResult 输出结构

| 字段 | 类型 | 说明 |
|------|------|------|
| finalAnswer | String | 最终答案 |
| steps | List<Step> | 所有推理步骤 |
| success | boolean | 是否执行成功 |
| iterations | int | 实际迭代次数 |
| totalTimeMs | long | 总耗时（毫秒） |

---

## 四、DAG 工作流引擎

### 4.1 架构设计

```
WorkflowDefinition（DAG 定义）
├── startNodeId: 起始节点
├── nodes: Map<String, WorkflowNode>（节点集合）
└── edges: List<WorkflowEdge>（条件边集合）

WorkflowEngine（执行引擎）
├── 循环检测：visited HashSet 防止无限循环
├── 顺序执行：按边的条件评估结果决定下一节点
└── 异常处理：节点失败时标记 state.failed
```

### 4.2 节点类型（5 种）

| 节点类型 | 类名 | 说明 |
|----------|------|------|
| START | `StartNode` | 起始节点，初始化工作流状态 |
| LLM | `LlmNode` | 调用大模型，支持模板变量渲染 |
| CONDITION | `ConditionNode` | SpEL 条件判断，输出 SUCCESS/FAILURE |
| TOOL | `ToolNode` | 执行工具调用 |
| END | `EndNode` | 终止节点，`isTerminal()` 返回 true |

### 4.3 LlmNode 详解

- **模板渲染**：支持 `{{variable}}` 占位符，从 WorkflowState 变量中替换
- **独立参数**：每个 LlmNode 可配置独立的 temperature
- **输出绑定**：LLM 响应存入 `state.setVariable(outputVariable, response)`

### 4.4 ConditionNode 详解

- **SpEL 表达式引擎**：使用 Spring Expression Language 评估条件
- **变量注入**：WorkflowState 中所有变量可在表达式中引用
- **结果分支**：条件为 true 时输出 trueResult，否则输出 falseResult
- **容错**：表达式求值异常时默认返回 false

### 4.5 WorkflowEdge（条件边）

- **无条件边**：`condition` 为 null 或空时，始终通过
- **条件边**：使用 SpEL 表达式评估，支持 `#state` 变量访问
- **边评估顺序**：按定义顺序依次评估，首个通过的边确定下一节点
- **无匹配边**：所有边都不满足条件时，工作流结束

### 4.6 循环检测

- 使用 `HashSet<String> visited` 记录已访问节点
- 重复访问同一节点时抛出 `AgentException(WORKFLOW_ERROR, "工作流存在循环")`

---

## 五、多 Agent 编排系统

### 5.1 编排架构

```
用户输入 → TaskDecomposer(任务分解) → AgentOrchestrator(编排执行) → ResultAggregator(结果汇总) → 最终答案
```

### 5.2 TaskDecomposer（任务分解器）

**工作原理**：
- 使用 LLM 将复杂任务自动分解为 2-5 个独立子任务
- 输出格式：JSON 数组 `[{id, name, description, input}]`
- **容错**：JSON 解析失败时回退为单任务执行

**Prompt 设计要点**：
- 子任务必须独立，可并行执行
- 明确依赖关系
- 严格限制子任务数量（2-5 个）

### 5.3 AgentOrchestrator（多 Agent 编排器）

**两种执行模式**：

| 模式 | 方法 | 特点 |
|------|------|------|
| 串行执行 | `execute()` | 依次创建 SubAgent 执行每个子任务 |
| 并行执行 | `executeParallel()` | 线程池并发执行，最多 5 个并发线程 |

**并行执行细节**：
- 线程池大小：`min(tasks.size(), 5)`
- 超时时间：每个子任务 60 秒
- 异常处理：单个子任务失败不影响其他任务
- 资源释放：执行完成后自动销毁 SubAgent

### 5.4 ResultAggregator（结果汇总器）

**工作原理**：
- 使用 LLM 将多个子任务结果整合为统一答案
- 去除重复信息
- 按逻辑顺序组织
- 冲突时以最新/最可靠结果为准

### 5.5 SubAgent（子代理）

**特性**：
- 独立上下文：每个 SubAgent 有独立的 ChatMemory（10 轮历史）
- 动态生命周期：按需创建，任务完成后销毁
- 角色定制：每个 SubAgent 有独立的 system prompt
- 资源隔离：不污染主 Agent 的对话上下文

---

## 六、安全护栏系统

### 6.1 输入护栏（InputGuardrail）

**检测项**：

| 检测类型 | 规则 | 处理方式 |
|----------|------|----------|
| 空输入检测 | `input == null \|\| input.isBlank()` | 阻断 |
| 长度超限 | `> 5000 字符` | 阻断 |
| Prompt 注入 | 10 个注入模式匹配 | 阻断 |
| 敏感内容 | 14 个敏感词模式 | 阻断 |

**Prompt 注入检测模式**：
```
"忽略以上指令", "请忘记之前的要求", "无视前面的指示",
"作为一个黑客", "绕过安全检查", "执行以下代码",
"请忽略", "请忽视", "忽略所有", "覆盖指令"
```

**敏感内容模式**：
```
"密码", "口令", "secret", "token", "api.*key",
"身份证", "银行卡", "手机号", "住址",
"攻击", "入侵", "破解", "钓鱼"
```

**返回结构**：`GuardrailResult(allowed, content, violations)`

### 6.2 输出护栏（OutputGuardrail）

**PII 脱敏规则**：

| PII 类型 | 正则模式 | 脱敏结果 |
|----------|----------|----------|
| 身份证号 | 18 位身份证格式 | `***身份证号***` |
| 银行卡号 | 11-15 位纯数字 | `***银行卡号***` |
| 手机号 | 1[3-9] 开头 11 位 | `***手机号***` |
| 邮箱 | 标准邮箱格式 | `***邮箱***` |

**有害内容检测**：
```
"攻击", "入侵", "破解", "窃取", "诈骗",
"色情", "暴力", "恐怖", "反动"
```
检测到有害内容时返回：`[内容审核不通过] 输出包含违规内容`

### 6.3 危险操作检测（DangerousActionDetector）

**检测关键词**（32 个）：
- 删除类：删除、delete、remove、drop、clear、purge
- 禁止类：禁令、禁止、禁用、ban、block、disable
- 敏感操作：修改密码、转账、支付、付款
- 系统操作：清空、重置、卸载、关闭、关闭服务
- 发布类：发布、发布到生产、deploy、publish

**工具名称正则检测**：
```
.*delete.*, .*remove.*, .*drop.*,
.*update.*pass.*, .*reset.*pass.*,
.*pay.*, .*transfer.*, .*ban.*, .*block.*, .*disable.*
```

**二次确认机制**（ActionPendingStore）：
1. 检测到危险操作 → 创建 PendingAction（状态：PENDING）
2. 返回警告信息给用户
3. 用户确认（confirm）→ 执行操作
4. 用户拒绝（reject）→ 取消操作
5. 超时清理：自动清除过期的 PendingAction

---

## 七、弹性容错机制

### 7.1 熔断器（CircuitBreaker）

**三态状态机**：

```
CLOSED ──(连续失败≥5次)──→ OPEN ──(30秒超时)──→ HALF_OPEN
  ↑                                                    │
  └──────────────(下一次成功)───────────────────────────┘
```

| 参数 | 值 | 说明 |
|------|------|------|
| FAILURE_THRESHOLD | 5 | 触发熔断的连续失败次数 |
| RESET_TIMEOUT | 30000ms | 熔断器开启后的恢复等待时间 |

**使用方式**：
```java
circuitBreaker.execute(
    () -> normalOperation(),    // 正常操作
    () -> fallbackOperation()   // 降级操作
);
```

### 7.2 降级处理器（FallbackHandler）

**降级场景**：

| 场景 | 方法 | 返回格式 |
|------|------|----------|
| 模型服务不可用 | `handleModelError()` | `[服务暂时不可用] {message}` |
| 工具调用失败 | `handleToolError()` | `[工具调用失败] {toolName}: {error}` |
| RAG 检索失败 | `handleRagError()` | `[知识库检索失败] {error}` |
| MCP 服务不可用 | `handleMcpError()` | `[MCP服务不可用] {url}: {error}` |
| 通用错误 | `handleGenericError()` | `[操作失败] {operation}: {error}` |

---

## 八、成本控制系统

### 8.1 配额管理器（QuotaManager）

| 配置 | 值 | 说明 |
|------|------|------|
| 每日配额 | 100,000 tokens | 单会话每日最大 Token 消耗 |

**核心方法**：
- `checkQuota(sessionId, requiredTokens)` — 检查是否超限
- `getRemainingQuota(sessionId)` — 获取剩余配额
- `enforceQuota(sessionId, requiredTokens)` — 强制校验，超限抛异常

### 8.2 Token 追踪器（TokenUsageTracker）

**数据结构**：
```
SessionUsage
├── sessionId
├── startTime
└── modelUsages: Map<String, ModelUsage>
    └── ModelUsage(model, promptTokens, completionTokens)
```

**功能**：
- 按会话维度记录 Token 使用量
- 按模型维度细分（支持多模型切换场景）
- 支持生成使用报告（UsageReport）
- 支持获取活跃会话数

---

## 九、链路追踪系统

### 9.1 TraceContext（追踪上下文）

- **存储方式**：ThreadLocal（线程级隔离）
- **生命周期**：`start()` → 使用 → `clear()`
- **核心字段**：traceId（UUID）、sessionId、currentSpanId、metadata

### 9.2 TraceSpan（追踪跨度）

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| spanId | String | 当前 Span 唯一标识 |
| parentSpanId | String | 父 Span ID（支持嵌套调用链） |
| traceId | String | 所属 Trace ID |
| operationName | String | 操作名称 |
| startTime | long | 开始时间戳 |
| endTime | long | 结束时间戳 |
| status | String | 执行状态 |
| input | String | 操作输入 |
| output | String | 操作输出 |
| errorMessage | String | 错误信息 |
| metadata | Map | 扩展元数据 |

**衍生指标**：`getDurationMs()` 计算执行耗时

---

## 十、MCP 协议系统

### 10.1 MCP Client（协议客户端）

**双协议自动检测**：
1. 优先尝试 **Streamable HTTP**（MCP 2025-03-26 规范）
2. 失败后自动回退到 **SSE**（MCP 2024-11-05 规范）
3. 两种协议均失败时抛出详细错误信息

**连接管理**：
- 动态连接/断开：按 URL 管理 MCP 服务
- 状态维护：`clientsByUrl`、`serverInfoByUrl`、`toolCallbacksByUrl`
- 持久化存储：URL 列表存入 `src/main/resources/data/mcp-servers.json`
- 应用重启后可自动恢复连接

**工具日志代理**：
- 每个 MCP 工具自动包装日志代理
- 记录：工具名称、入参（截断 200 字符）、耗时、结果（截断 300 字符）
- 异常时记录错误详情

### 10.2 McpHealthChecker（健康检查器）

- **检查频率**：每 30 秒（`@Scheduled(fixedRate = 30000)`）
- **检查逻辑**：遍历所有已连接服务，检测连接状态
- **故障处理**：不健康时自动断开并触发重连

### 10.3 McpReconnectManager（重连管理器）

**指数退避策略**：`[1s, 2s, 4s, 8s, 16s]`

- 最多重试 5 次
- 每次重试间隔递增
- 重连成功后自动恢复
- 所有重试耗尽后停止尝试

### 10.4 MCP Server（服务端）

项目同时实现了 MCP Server 端，基于 Spring AI MCP WebMVC Server：
- 服务名称：`AiAgentDemo-MCP-Server`
- 版本：`1.0.0`
- 对外暴露 Agent 能力为标准 MCP 工具

---

## 十一、技能系统（Skill）

### 11.1 技能文件格式

**简单格式**（向后兼容）：
```markdown
---
name: translate
description: 翻译文本
---

请将以下内容翻译为英文：
{{input}}
```

**多参数格式**：
```markdown
---
name: code_review
description: 代码审查
parameters:
  - name: code
    description: 需要审查的代码
    required: true
  - name: language
    description: 编程语言
    required: false
    default: auto
---

请对以下 {{language}} 代码进行审查：
{{code}}
```

### 11.2 技能管理功能

| 操作 | 方法/API | 说明 |
|------|----------|------|
| 自动加载 | 构造时扫描 `classpath:skill/*.md` | 启动时自动发现 |
| 热重载 | `POST /api/skill/reload` | 运行时重新加载所有技能 |
| 动态添加 | `POST /api/skill/upload`（MultipartFile） | 上传 .md 文件添加 |
| 动态移除 | `POST /api/skill/remove` | 按名称移除 |
| 列表查询 | `GET /api/skill/list` | 获取所有技能及参数信息 |

### 11.3 内置技能列表

| 技能文件 | 功能 |
|----------|------|
| `translate.md` | 文本翻译 |
| `code_review.md` | 代码审查 |
| `code_explain.md` | 代码解释 |
| `summarize.md` | 文本总结 |
| `sql_generator.md` | SQL 生成 |
| `api_doc_generator.md` | API 文档生成 |
| `tech_trivia.md` | 技术知识问答 |

---

## 十二、命令系统（Command）

### 12.1 与 Skill 的区别

| 对比项 | Command | Skill |
|--------|---------|-------|
| 注册方式 | 不注册为 ToolCallback | 注册为 ToolCallback |
| 调用方式 | 用户通过 `/命令名` 直接调用 | LLM 自主决策是否调用 |
| 文件格式 | 纯 Prompt 模板（无 front matter） | YAML front matter + Prompt |
| 目录 | `classpath:command/*.md` | `classpath:skill/*.md` |
| 命名 | 文件名即命令名 | front matter 中定义 name |

### 12.2 内置命令

| 命令 | 文件 | 功能 |
|------|------|------|
| `/review` | `review.md` | 代码审查 |
| `/summarize` | `summarize.md` | 文本总结 |
| `/translate` | `translate.md` | 文本翻译 |

### 12.3 命令执行流程

```
用户输入 "/review 代码内容" → CommandController 解析命令名 → 
查找 CommandDefinition → buildPrompt(template, input) → 
agentCore.chat(sessionId, prompt) → 返回结果
```

---

## 十三、工具系统

### 13.1 工具架构

```
InnerTool（接口）
├── RagTool          → knowledge_search
├── SubAgentTool     → create_sub_agent, chat_with_sub_agent, destroy_sub_agent
├── SkillMcpTool     → 动态生成技能工具
├── SubAgentMcpTool  → SubAgent 相关 MCP 工具
├── GetWeatherTool   → get_weather
└── GetStockPriceTool → get_stock_price
```

### 13.2 工具注册机制

- 启动时自动扫描所有 `InnerTool` Bean
- 调用 `tool.loadToolCallbacks()` 获取 ToolCallback 列表
- 统一注册到 `AgentCore.toolCallbacks`
- MCP 远程工具在连接时动态注册/断开时移除

### 13.3 完整工具列表

| 工具名 | 来源 | 功能描述 |
|--------|------|----------|
| knowledge_search | RagTool | 知识库检索 |
| create_sub_agent | SubAgentTool | 创建子代理 |
| chat_with_sub_agent | SubAgentTool | 与子代理对话 |
| destroy_sub_agent | SubAgentTool | 销毁子代理 |
| get_weather | GetWeatherTool | 查询天气信息 |
| get_stock_price | GetStockPriceTool | 查询股票价格 |
| [动态] | SkillMcpTool | 根据加载的 Skill 动态生成 |
| [远程] | MCP Server | 通过 MCP 协议连接的远程工具 |

---

## 十四、API 接口完整文档

### 14.1 对话接口（ChatController）

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/chat` | 非流式对话 | `{message, sessionId?}` |
| POST | `/api/chat/stream` | 流式对话（SSE） | `{message, sessionId?}` |

### 14.2 命令接口（CommandController）

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| GET | `/api/command/list` | 获取命令列表 | — |
| POST | `/api/command/execute` | 执行命令（非流式） | `{name, input?, sessionId?}` |
| POST | `/api/command/execute/stream` | 执行命令（流式） | `{name, input?, sessionId?}` |

### 14.3 管理接口（ManagerController）

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/manage/clear-memory` | 清空会话历史 | `{sessionId?}` |
| POST | `/api/manage/switch-model` | 切换模型 | `{baseUrl, apiKey, chatModel, ...}` |
| GET | `/api/manage/model-params` | 获取模型参数 | — |
| POST | `/api/manage/model-params` | 更新模型参数 | `{temperature?, maxTokens?, topP?}` |
| POST | `/api/manage/mcp/connect` | 连接 MCP 服务 | `{url}` |
| POST | `/api/manage/mcp/disconnect` | 断开 MCP 服务 | `{url}` |
| GET | `/api/manage/mcp/list` | 获取 MCP 服务列表 | — |

### 14.4 技能接口（SkillController）

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| GET | `/api/skill/list` | 获取技能列表 | — |
| POST | `/api/skill/upload` | 上传技能文件 | `multipart/form-data (file)` |
| POST | `/api/skill/reload` | 重载所有技能 | — |
| POST | `/api/skill/remove` | 移除技能 | `{name}` |

### 14.5 文件接口（FileController）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/file/upload` | 上传知识库文件 |

### 14.6 操作确认接口（ActionController）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/action/confirm` | 确认危险操作 |
| POST | `/api/action/reject` | 拒绝危险操作 |
| GET | `/api/action/pending` | 获取待确认操作列表 |

### 14.7 调试接口（DebugController）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/debug/status` | 系统状态信息 |
| GET | `/api/debug/trace` | 链路追踪信息 |

---

## 十五、配置项完整参考

### 15.1 LLM 配置

```properties
# 基础 URL（兼容 OpenAI 协议的任何服务）
spring.ai.openai.base-url=https://open.bigmodel.cn/api/paas/v4
spring.ai.openai.api-key=your-api-key

# 模型配置
spring.ai.openai.chat.options.model=glm-4.6
spring.ai.openai.embedding.options.model=embedding-3

# API 路径配置
spring.ai.openai.chat.completions-path=/chat/completions
spring.ai.openai.embedding.embeddings-path=/embeddings
```

### 15.2 Agent 配置

```properties
# System Prompt
agent.system-prompt=你是一个智能助手...

# 推理参数
agent.temperature=0.7          # 温度（0.0~2.0）
agent.max-tokens=2048          # 最大输出 Token
agent.top-p=1.0                # Top-P 采样

# ReAct 限制
agent.max-iterations=10        # 最大推理迭代次数

# 多 Agent 编排
agent.orchestration.enabled=true
agent.orchestration.mode=hierarchical
agent.orchestration.max-agents=10
agent.orchestration.timeout-seconds=300
```

### 15.3 记忆配置

```properties
memory.max-rounds=20                    # 主 Agent 最大历史轮数
memory.max-assistant-messages=3         # 最多保留 Assistant 回复数
memory.compression-threshold=15         # 触发摘要压缩的消息数
memory.expire-minutes=1440              # 会话过期时间（分钟）
memory.sub-agent-max-rounds=10          # SubAgent 最大历史轮数
memory.sub-agent-max-assistant-messages=3
```

### 15.4 RAG 配置

```properties
rag.chunk-size=500         # 分块大小（字符）
rag.chunk-overlap=50       # 块间重叠（字符）
rag.top-k=3                # 最终返回文档数
rag.recall-count=9         # 多路召回候选数
rag.rerank.path=/rerank    # Rerank API 路径
rag.rerank.model=rerank    # Rerank 模型名
```

### 15.5 MCP Server 配置

```properties
spring.ai.mcp.server.name=AiAgentDemo-MCP-Server
spring.ai.mcp.server.version=1.0.0
```

---

## 十六、异常处理体系

### 16.1 异常类型

| 异常类 | 用途 |
|--------|------|
| `AgentException` | Agent 运行时通用异常 |
| `GuardrailException` | 安全护栏检测异常 |

### 16.2 错误码（ErrorCode）

| 错误码 | 说明 |
|--------|------|
| TOOL_ERROR | 工具调用错误 |
| WORKFLOW_ERROR | 工作流执行错误 |
| MODEL_ERROR | 模型调用错误 |
| QUOTA_EXCEEDED | 配额超限 |

### 16.3 全局异常处理（GlobalExceptionHandler）

- 统一拦截所有 Controller 层异常
- 返回标准化 `ErrorResponse` 格式
- 日志记录完整堆栈

---

## 十七、部署与运行

### 17.1 环境要求

- JDK 21+
- Maven 3.8+
- 可用的 LLM API Key（ZhipuAI / DeepSeek / Moonshot 等 OpenAI 兼容服务）

### 17.2 启动方式

```bash
cd E:\aiagentdemo-20260713
mvn spring-boot:run
```

### 17.3 启动输出

```
========================================
  AI Agent 已就绪（已加载 N 个工具）
  HTTP API: POST /api/chat
  MCP 管理: GET/POST /api/mcp/*
========================================
```

### 17.4 前端访问

内置静态页面：`http://localhost:8080/index.html`

---

## 十八、项目依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.5 | 应用框架 |
| Spring AI BOM | 2.0.0-M4 | AI 能力抽象层 |
| Spring AI OpenAI | — | OpenAI 兼容接口 |
| Spring AI MCP Client | — | MCP 协议客户端 |
| Spring AI MCP Server WebMVC | — | MCP 协议服务端 |
| FastJSON | 1.2.83 | JSON 序列化 |
| Caffeine | 3.1.8 | 高性能本地缓存 |
| Java | 21 | 运行时环境 |

---

**文档版本**：v3.0  
**生成日期**：2026-07-14  
**项目地址**：https://github.com/tang1157026291-coder/aiagentdemo
