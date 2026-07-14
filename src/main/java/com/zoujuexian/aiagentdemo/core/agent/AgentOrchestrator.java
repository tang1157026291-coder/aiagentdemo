package com.zoujuexian.aiagentdemo.core.agent;

import com.zoujuexian.aiagentdemo.core.SubAgent;
import com.zoujuexian.aiagentdemo.core.SubAgentManager;
import com.zoujuexian.aiagentdemo.service.rag.RagService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class AgentOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);

    /** 共享线程池，避免每次调用创建新线程池 */
    private final ExecutorService sharedExecutor = new ThreadPoolExecutor(
            2, 5, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            r -> {
                Thread t = new Thread(r, "agent-orchestrator-" + UUID.randomUUID().toString().substring(0, 4));
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Resource
    private ChatClient chatClient;

    @Resource
    private SubAgentManager subAgentManager;

    @Resource
    private TaskDecomposer taskDecomposer;

    @Resource
    private ResultAggregator resultAggregator;

    public String execute(String originalQuestion, List<ToolCallback> tools) {
        logger.info("开始多Agent编排执行: {}", originalQuestion);

        List<AgentTask> tasks = taskDecomposer.decompose(originalQuestion);
        logger.info("任务分解完成，共 {} 个子任务", tasks.size());

        List<AgentResult> results = new ArrayList<>();

        for (AgentTask task : tasks) {
            String agentName = generateAgentName(task.getName());
            String agentRole = generateAgentRole(task.getName());
            
            SubAgent agent = subAgentManager.create(agentName, agentRole);
            
            String response = agent.chat(task.getInput());
            AgentResult result = new AgentResult(
                    task.getId(),
                    agent.getId(),
                    agent.getName(),
                    response
            );
            results.add(result);

            subAgentManager.destroy(agent.getId());
        }

        String finalAnswer = resultAggregator.aggregate(originalQuestion, results);
        logger.info("多Agent编排执行完成");

        return finalAnswer;
    }

    public String executeParallel(String originalQuestion, List<ToolCallback> tools) {
        logger.info("开始多Agent并行执行: {}", originalQuestion);

        List<AgentTask> tasks = taskDecomposer.decompose(originalQuestion);
        logger.info("任务分解完成，共 {} 个子任务", tasks.size());

        List<Future<AgentResult>> futures = new ArrayList<>();

        for (AgentTask task : tasks) {
            futures.add(sharedExecutor.submit(() -> {
                String agentName = generateAgentName(task.getName());
                String agentRole = generateAgentRole(task.getName());
                
                SubAgent agent = subAgentManager.create(agentName, agentRole);
                String response = agent.chat(task.getInput());
                
                AgentResult result = new AgentResult(
                        task.getId(),
                        agent.getId(),
                        agent.getName(),
                        response
                );
                
                subAgentManager.destroy(agent.getId());
                return result;
            }));
        }

        List<AgentResult> results = new ArrayList<>();
        for (Future<AgentResult> future : futures) {
            try {
                results.add(future.get(60, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.error("子任务执行失败", e);
                AgentResult errorResult = new AgentResult();
                errorResult.setErrorMessage(e.getMessage());
                results.add(errorResult);
            }
        }

        String finalAnswer = resultAggregator.aggregate(originalQuestion, results);
        logger.info("多Agent并行执行完成");

        return finalAnswer;
    }

    private String generateAgentName(String taskName) {
        return taskName + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateAgentRole(String taskName) {
        return "你是一个专业的" + taskName + "助手。请根据输入完成相应的任务，输出详细、准确的结果。";
    }
}
