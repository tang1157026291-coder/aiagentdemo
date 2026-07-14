package com.zoujuexian.aiagentdemo.core.workflow;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class WorkflowEdge {

    private String fromNodeId;
    private String toNodeId;
    private String condition;

    public WorkflowEdge() {}

    public WorkflowEdge(String fromNodeId, String toNodeId) {
        this(fromNodeId, toNodeId, null);
    }

    public WorkflowEdge(String fromNodeId, String toNodeId, String condition) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.condition = condition;
    }

    public boolean evaluate(WorkflowState state) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        try {
            EvaluationContext context = new StandardEvaluationContext();
            context.setVariable("state", state);
            SpelExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(condition);
            return exp.getValue(context, Boolean.class);
        } catch (Exception e) {
            return true;
        }
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public void setFromNodeId(String fromNodeId) {
        this.fromNodeId = fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}
