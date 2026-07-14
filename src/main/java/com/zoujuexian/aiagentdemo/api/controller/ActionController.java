package com.zoujuexian.aiagentdemo.api.controller;

import com.zoujuexian.aiagentdemo.api.controller.dto.ChatResponse;
import com.zoujuexian.aiagentdemo.core.action.ActionPendingStore;
import com.zoujuexian.aiagentdemo.core.action.ActionPendingStore.PendingAction;
import com.zoujuexian.aiagentdemo.core.AgentCore;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/action", produces = MediaType.APPLICATION_JSON_VALUE)
public class ActionController {

    @Resource
    private ActionPendingStore pendingStore;

    @Resource
    private AgentCore agentCore;

    @GetMapping("/pending/{sessionId}")
    public Map<String, PendingAction> listPending(@PathVariable String sessionId) {
        return pendingStore.getAllBySession(sessionId);
    }

    @PostMapping("/confirm")
    public ChatResponse confirmAction(@RequestBody Map<String, String> request) {
        String actionId = request.get("actionId");
        if (actionId == null || actionId.isBlank()) {
            return ChatResponse.fail("actionId 不能为空");
        }

        PendingAction action = pendingStore.get(actionId);
        if (action == null) {
            return ChatResponse.fail("未找到待确认的操作");
        }

        pendingStore.confirm(actionId);
        return ChatResponse.ok("操作已确认，正在执行", 
                Map.of("actionId", actionId, "toolName", action.getToolName(), 
                       "toolInput", action.getToolInput()));
    }

    @PostMapping("/reject")
    public ChatResponse rejectAction(@RequestBody Map<String, String> request) {
        String actionId = request.get("actionId");
        if (actionId == null || actionId.isBlank()) {
            return ChatResponse.fail("actionId 不能为空");
        }

        boolean removed = pendingStore.reject(actionId);
        if (removed) {
            return ChatResponse.ok("操作已取消");
        }
        return ChatResponse.fail("未找到待确认的操作");
    }
}
