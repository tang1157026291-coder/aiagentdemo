package com.zoujuexian.aiagentdemo.core;

import com.zoujuexian.aiagentdemo.service.tool.InnerTool;
import com.zoujuexian.aiagentdemo.service.rag.RagService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Agent 核心编排器
 * <p>
 * 基于 Spring AI ChatClient 实现，自动支持 ReAct 循环（工具调用）。
 * 职责单一：管理对话记忆 + 工具回调 + 模型调用，不关心工具来源。
 */
@Component
public class AgentCore implements InitializingBean , ApplicationContextAware {

    /** 按 sessionId 隔离的对话记忆，支持多客户端并发 */
    private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();
    private String systemPromptText;
    private final List<ToolCallback> toolCallbacks = new ArrayList<>();
    private ApplicationContext applicationContext;

    @Resource
    private ChatClient chatClient;

    @Resource
    private IntentRecognizer intentRecognizer;

    @Resource
    private RagService ragService;

    @Resource
    private SubAgentManager subAgentManager;

    /** 模型推理参数 */
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;
    private Double topP = 1.0;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.systemPromptText = "你是一个智能助手，具备知识库检索（RAG）、工具调用（Function Calling）、"
                + "技能执行（Skill）、MCP 协议连接和子代理（SubAgent）等能力。\n"
                + "当遇到需要独立上下文记忆的复杂子任务时，你可以创建 SubAgent 来处理。\n"
                + "请根据用户的问题，合理选择使用工具或直接回答。\n"
                + "回答时请简洁准确，必要时引用工具返回的结果。";

        // 初始化 SubAgentManager，共享 ChatClient
        subAgentManager.setChatClient(chatClient);

        // 自动发现所有 InnerTool Bean，统一加载 ToolCallback
        Collection<InnerTool> innerTools = applicationContext.getBeansOfType(InnerTool.class).values();
        List<ToolCallback> allCallbacks = new ArrayList<>();
        for (InnerTool tool : innerTools) {
            try {
                allCallbacks.addAll(tool.loadToolCallbacks());
            } catch (Exception exception) {
                System.err.println("[Tool] " + tool.getClass().getSimpleName() + " 加载失败: " + exception.getMessage());
            }
        }

        registerToolCallbacks(allCallbacks);

        System.out.println("\n========================================");
        System.out.println("  AI Agent 已就绪（已加载 " + allCallbacks.size() + " 个工具）");
        System.out.println("  HTTP API: POST /api/chat");
        System.out.println("  MCP 管理: GET/POST /api/mcp/*");
        System.out.println("========================================\n");
    }

    /**
     * 运行时切换模型
     *
     * @param modelConfig 新的模型配置
     */
    public void switchModel(ModelConfig modelConfig) {
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(modelConfig.getBaseUrl())
                .apiKey(modelConfig.getApiKey());

        if (modelConfig.getCompletionsPath() != null) {
            apiBuilder.completionsPath(modelConfig.getCompletionsPath());
        }

        OpenAiApi openAiApi = apiBuilder.build();

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(modelConfig.getChatModel())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
        ;
        this.chatClient = ChatClient.builder(chatModel).build();
        subAgentManager.setChatClient(this.chatClient);
        System.out.println("[模型切换] 已切换到: " + modelConfig);
    }

    /**
     * 批量注册工具回调
     */
    public void registerToolCallbacks(List<ToolCallback> callbacks) {
        toolCallbacks.addAll(callbacks);
    }

    /**
     * 批量注册工具回调
     */
    public void registerToolCallbacks(ToolCallback... callbacks) {
        toolCallbacks.addAll(Arrays.asList(callbacks));
    }

    /**
     * 移除指定的工具回调
     */
    public void removeToolCallbacks(List<ToolCallback> callbacks) {
        toolCallbacks.removeAll(callbacks);
    }

    /**
     * 获取或创建指定 sessionId 的对话记忆
     */
    private ChatMemory getOrCreateMemory(String sessionId) {
        return sessionMemories.computeIfAbsent(sessionId, id -> {
            ChatMemory memory = ChatMemory.forMainAgent(chatClient);
            memory.setSystemPrompt(systemPromptText);
            return memory;
        });
    }

    /**
     * 设置自定义 system prompt（全局生效，影响后续新建的会话）
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPromptText = systemPrompt;
        // 同步更新所有已有会话的 system prompt
        sessionMemories.values().forEach(memory -> memory.setSystemPrompt(systemPrompt));
    }

    /**
     * 设置模型推理参数
     */
    public void setModelParams(Double temperature, Integer maxTokens, Double topP) {
        if (temperature != null) this.temperature = temperature;
        if (maxTokens != null) this.maxTokens = maxTokens;
        if (topP != null) this.topP = topP;
    }

    public Double getTemperature() { return temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public Double getTopP() { return topP; }

    /**
     * 构建当前模型推理参数
     */
    private OpenAiChatOptions buildChatOptions() {
        return OpenAiChatOptions.builder()
                .temperature(temperature)
                .maxTokens(maxTokens)
                .topP(topP)
                .build();
    }

    /**
     * 与 Agent 对话
     * <p>
     * 流程：意图识别 → 按需注入 RAG 上下文 → 大模型调用（含工具调用）
     *
     * @param sessionId 会话 ID，用于隔离不同客户端的对话记忆
     * @param userInput 用户输入
     * @return 模型回复
     */
    public String chat(String sessionId, String userInput) {
        ChatMemory memory = getOrCreateMemory(sessionId);

        // 1. 意图识别
        Intent intent = intentRecognizer.recognize(userInput);

        // 2. 如果是 RAG 意图，先检索知识库并注入上下文
        if (intent == Intent.RAG && ragService.isKnowledgeLoaded()) {
            String ragContext = ragService.query(userInput);
            if (ragContext != null && !ragContext.isBlank()) {
                String enrichedInput = "以下是从知识库中检索到的相关参考资料，请结合这些资料回答用户的问题：\n\n"
                        + ragContext + "\n\n用户问题：" + userInput;
                memory.addMessage(new UserMessage(enrichedInput));
            } else {
                memory.addMessage(new UserMessage(userInput));
            }
        } else {
            memory.addMessage(new UserMessage(userInput));
        }

        // 3. 构建 Prompt（带模型参数）并调用大模型（getMessages 内部自动触发摘要压缩）
        List<Message> messages = memory.getMessages();
        Prompt prompt = new Prompt(messages, buildChatOptions());

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt(prompt);

        if (!toolCallbacks.isEmpty()) {
            requestSpec.toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));
        }

        String response = requestSpec.call().content();

        memory.addMessage(new AssistantMessage(response != null ? response : ""));

        return response != null ? response : "";
    }

    /**
     * 与 Agent 流式对话（核心调度方法）
     * <p>
     * 完整处理流程：意图识别 → RAG 知识增强 → 工具绑定 → 流式调用大模型 → 记忆闭环。
     * 与 chat() 的区别：本方法以 Flux 流逐字返回，适合前端打字机效果；chat() 为阻塞式同步返回。
     * <p>
     * <b>RAG 意图处理逻辑：</b><br>
     * 当识别为 {@link Intent#RAG} 时，系统先从本地知识库检索相关文档，
     * 将检索结果注入用户问题前作为上下文，使 LLM 基于知识库而非预训练知识作答。
     * <p>
     * <b>工具调用逻辑：</b><br>
     * 通过 {@code toolCallbacks} 动态绑定已注册的工具（内部工具、MCP 远程工具等）。
     * LLM 自主判断是否需要调用工具，Spring AI 自动处理 Function Calling 往返。
     * <p>
     * <b>流式记忆闭环：</b><br>
     * 流式输出期间通过 {@link StringBuilder} 累加完整文本，
     * 流结束后将完整响应保存为 {@link AssistantMessage}，确保多轮对话上下文连续。
     *
     * @param sessionId 会话唯一标识，用于隔离不同用户的对话历史（如不同浏览器标签页）
     * @param userInput 用户原始输入文本
     * @return Reactor {@link Flux} 流，每个元素为一个文本片段（token），前端可实时订阅渲染
     */
    public Flux<String> chatStream(String sessionId, String userInput) {
        // ─────────────────────────────────────────────
        // 阶段 0：获取会话记忆
        // 根据 sessionId 从内存中获取或创建新的 ChatMemory 实例。
        // ChatMemory 内部维护一个 List<Message>，存储用户和助手的多轮对话历史。
        // 同时内置摘要压缩机制，当消息总长度超过阈值时自动压缩早期对话。
        // ─────────────────────────────────────────────
        ChatMemory memory = getOrCreateMemory(sessionId);

        // ─────────────────────────────────────────────
        // 阶段 1：意图识别（Intent Recognition）
        // 使用轻量级规则/关键词匹配用户输入意图，例如：
        // - "查一下 HashMap" → RAG 意图（触发知识库检索）
        // - "今天北京天气"  → TOOL 意图（可能触发天气工具）
        // - "你好"          → CHAT 意图（普通闲聊，不走 RAG）
        // 意图分类定义参见 {@link Intent} 枚举。
        // ─────────────────────────────────────────────
        Intent intent = intentRecognizer.recognize(userInput);

        // ─────────────────────────────────────────────
        // 阶段 2：RAG 知识增强（可选）
        // 仅当同时满足以下两个条件时执行：
        //   1. 意图识别结果为 RAG（需要知识库支持的问题）
        //   2. 知识库已完成加载（ragService.isKnowledgeLoaded()）
        //
        // 执行步骤：
        //   a) 调用 ragService.query(userInput) 从向量数据库检索 Top-K 相关文档
        //   b) 将检索结果与用户原始问题拼接，构造 enrichedInput
        //   c) 将 enrichedInput 作为 UserMessage 存入记忆
        //
        // 拼接格式设计说明：
        //   明确告知 LLM "以下是从知识库中检索到的相关参考资料"，
        //   引导 LLM 优先参考检索内容，降低幻觉概率。
        // ─────────────────────────────────────────────
        if (intent == Intent.RAG && ragService.isKnowledgeLoaded()) {
            // 从向量数据库检索与用户输入语义相似的文档片段
            String ragContext = ragService.query(userInput);

            if (ragContext != null && !ragContext.isBlank()) {
                // 检索命中：将知识库内容作为上下文注入用户问题前
                String enrichedInput = "以下是从知识库中检索到的相关参考资料，请结合这些资料回答用户的问题：\n\n"
                        + ragContext + "\n\n用户问题：" + userInput;
                memory.addMessage(new UserMessage(enrichedInput));
            } else {
                // 检索未命中（知识库无相关内容）：直接保存原始问题，避免硬塞空上下文
                memory.addMessage(new UserMessage(userInput));
            }
        } else {
            // 非 RAG 意图 或 知识库未加载：直接保存原始用户输入
            memory.addMessage(new UserMessage(userInput));
        }

        // ─────────────────────────────────────────────
        // 阶段 3：构建 Prompt 并绑定工具
        //
        // 3.1 获取历史消息
        // memory.getMessages() 返回当前会话的全部消息列表（含刚刚添加的用户消息）。
        // 内部会自动触发摘要压缩：若消息总长度超过阈值，早期对话会被压缩为摘要，
        // 从而控制 Token 消耗，避免超出模型上下文窗口限制。
        //
        // 3.2 构建 Prompt 对象
        // new Prompt(messages, buildChatOptions()) 将消息列表和模型参数封装。
        // buildChatOptions() 通常配置 temperature、maxTokens、model 等参数。
        //
        // 3.3 绑定工具回调
        // toolCallbacks 是运行时动态维护的工具列表，包含：
        //   - 内部工具：天气查询、股票查询、RAG 工具等
        //   - 外部 MCP 工具：运行时通过 MCP Client 动态连接的远程工具
        // Spring AI 会将工具定义随请求发送给 LLM，LLM 自主决策是否调用。
        // ─────────────────────────────────────────────
        List<Message> messages = memory.getMessages();
        Prompt prompt = new Prompt(messages, buildChatOptions());

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt(prompt);

        if (!toolCallbacks.isEmpty()) {
            // 将工具列表转为数组传入，Spring AI 自动序列化为 OpenAI Function Calling 格式
            requestSpec.toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));
        }

        // ─────────────────────────────────────────────
        // 阶段 4：流式调用大模型 + 记忆闭环
        //
        // 4.1 创建累加器
        // Flux 流每次只返回一小段文本（token），需要 StringBuilder 逐段累加完整回复。
        //
        // 4.2 流式订阅操作符说明：
        //   - stream().content() ：向大模型发起流式请求，返回 Flux<String>
        //   - doOnNext()         ：每收到一个 token 时触发，追加到 fullResponse
        //   - doOnComplete()     ：流正常结束时触发，将完整文本保存为 AssistantMessage
        //   - doOnError()        ：流异常时触发，打印日志并保存空消息（避免记忆断裂）
        //
        // 记忆闭环设计说明：
        //   必须等待流结束后再保存完整回复到 memory，否则：
        //   a) 保存不完整内容 → 下一轮对话上下文错乱
        //   b) 不保存任何内容 → 下一轮对话丢失本轮上下文
        // ─────────────────────────────────────────────
        StringBuilder fullResponse = new StringBuilder();

        return requestSpec.stream().content()
                // 每收到一个 token，追加到累加器（此时前端已显示该 token）
                .doOnNext(fullResponse::append)
                // 流正常结束：将完整回复保存到会话记忆，实现多轮对话闭环
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    memory.addMessage(new AssistantMessage(response.isEmpty() ? "" : response));
                })
                // 流异常：打印错误日志，并保存空 AssistantMessage（保持消息轮次对齐）
                .doOnError(error -> {
                    System.err.println("[Stream] 流式对话异常: " + error.getMessage());
                    memory.addMessage(new AssistantMessage(""));
                });
    }

    /**
     * 清空指定会话的对话历史
     *
     * @param sessionId 会话 ID
     */
    public void clearMemory(String sessionId) {
        ChatMemory memory = sessionMemories.remove(sessionId);
        if (memory != null) {
            memory.clear();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
