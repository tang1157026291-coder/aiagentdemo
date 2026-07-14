package com.zoujuexian.aiagentdemo.core.action;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActionPendingStore {

    private final Map<String, PendingAction> pendingActions = new ConcurrentHashMap<>();

    public PendingAction create(String sessionId, String actionType, String actionDescription,
                                  String toolName, String toolInput, String originalMessage) {
        String actionId = UUID.randomUUID().toString();
        PendingAction action = new PendingAction(
                actionId, sessionId, actionType, actionDescription,
                toolName, toolInput, originalMessage, LocalDateTime.now()
        );
        pendingActions.put(actionId, action);
        return action;
    }

    public PendingAction get(String actionId) {
        return pendingActions.get(actionId);
    }

    public boolean confirm(String actionId) {
        PendingAction action = pendingActions.get(actionId);
        if (action != null) {
            action.setStatus("CONFIRMED");
            return true;
        }
        return false;
    }

    public boolean reject(String actionId) {
        PendingAction action = pendingActions.remove(actionId);
        return action != null;
    }

    public void remove(String actionId) {
        pendingActions.remove(actionId);
    }

    public Map<String, PendingAction> getAllBySession(String sessionId) {
        Map<String, PendingAction> result = new ConcurrentHashMap<>();
        pendingActions.forEach((id, action) -> {
            if (action.getSessionId().equals(sessionId) && "PENDING".equals(action.getStatus())) {
                result.put(id, action);
            }
        });
        return result;
    }

    public int cleanupExpired(int expireMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expireMinutes);
        int[] count = {0};
        pendingActions.entrySet().removeIf(entry -> {
            if (entry.getValue().getCreatedAt().isBefore(cutoff)) {
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }

    public static class PendingAction {
        private final String id;
        private final String sessionId;
        private final String actionType;
        private final String actionDescription;
        private final String toolName;
        private final String toolInput;
        private final String originalMessage;
        private final LocalDateTime createdAt;
        private String status = "PENDING";

        public PendingAction(String id, String sessionId, String actionType, 
                            String actionDescription, String toolName, String toolInput,
                            String originalMessage, LocalDateTime createdAt) {
            this.id = id;
            this.sessionId = sessionId;
            this.actionType = actionType;
            this.actionDescription = actionDescription;
            this.toolName = toolName;
            this.toolInput = toolInput;
            this.originalMessage = originalMessage;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public String getSessionId() { return sessionId; }
        public String getActionType() { return actionType; }
        public String getActionDescription() { return actionDescription; }
        public String getToolName() { return toolName; }
        public String getToolInput() { return toolInput; }
        public String getOriginalMessage() { return originalMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
