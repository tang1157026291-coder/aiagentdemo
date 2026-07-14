package com.zoujuexian.aiagentdemo.core.workflow.node;

import com.zoujuexian.aiagentdemo.core.workflow.WorkflowNode;
import com.zoujuexian.aiagentdemo.core.workflow.WorkflowState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

public class ConditionNode implements WorkflowNode {

    private final String id;
    private final String conditionExpression;
    private final String trueResult;
    private final String falseResult;

    public ConditionNode(String id, String conditionExpression) {
        this(id, conditionExpression, "SUCCESS", "FAILURE");
    }

    public ConditionNode(String id, String conditionExpression, String trueResult, String falseResult) {
        this.id = id;
        this.conditionExpression = conditionExpression;
        this.trueResult = trueResult;
        this.falseResult = falseResult;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return "CONDITION";
    }

    @Override
    public WorkflowState execute(WorkflowState state, ChatClient chatClient) {
        boolean result = evaluateCondition(state);
        state.setResult(result ? trueResult : falseResult);
        return state;
    }

    private boolean evaluateCondition(WorkflowState state) {
        try {
            EvaluationContext context = new StandardEvaluationContext();
            context.setVariable("state", state);
            for (Map.Entry<String, Object> entry : state.getVariables().entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
            SpelExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(conditionExpression);
            Boolean result = exp.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
