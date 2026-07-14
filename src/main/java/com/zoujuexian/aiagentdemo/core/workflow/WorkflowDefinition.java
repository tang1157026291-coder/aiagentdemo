package com.zoujuexian.aiagentdemo.core.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowDefinition {

    private String id;
    private String name;
    private String description;
    private String startNodeId;
    private final Map<String, WorkflowNode> nodes = new HashMap<>();
    private final List<WorkflowEdge> edges = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public void setStartNodeId(String startNodeId) {
        this.startNodeId = startNodeId;
    }

    public void addNode(WorkflowNode node) {
        nodes.put(node.getId(), node);
    }

    public WorkflowNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public Map<String, WorkflowNode> getNodes() {
        return nodes;
    }

    public void addEdge(WorkflowEdge edge) {
        edges.add(edge);
    }

    public List<WorkflowEdge> getEdgesFrom(String nodeId) {
        return edges.stream()
                .filter(e -> e.getFromNodeId().equals(nodeId))
                .toList();
    }

    public List<WorkflowEdge> getEdges() {
        return edges;
    }
}
