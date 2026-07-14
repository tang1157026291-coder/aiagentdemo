package com.zoujuexian.aiagentdemo.service.external;

import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Component
public class McpNamespaceManager {

    private final Map<String, String> namespaceToUrl = new HashMap<>();

    public String qualifyToolName(String serverUrl, String toolName) {
        String namespace = extractNamespace(serverUrl);
        return namespace + "__" + toolName;
    }

    public String extractNamespace(String serverUrl) {
        try {
            URL url = new URL(serverUrl);
            String host = url.getHost();
            return host.replace(".", "_").toLowerCase();
        } catch (MalformedURLException e) {
            return "mcp_" + Math.abs(serverUrl.hashCode());
        }
    }

    public String unqualifyToolName(String qualifiedName) {
        int idx = qualifiedName.indexOf("__");
        return idx >= 0 ? qualifiedName.substring(idx + 2) : qualifiedName;
    }

    public String getServerUrlByToolName(String qualifiedName) {
        int idx = qualifiedName.indexOf("__");
        if (idx < 0) {
            return null;
        }
        String namespace = qualifiedName.substring(0, idx);
        return namespaceToUrl.get(namespace);
    }

    public void registerNamespace(String namespace, String serverUrl) {
        namespaceToUrl.put(namespace, serverUrl);
    }

    public void unregisterNamespace(String namespace) {
        namespaceToUrl.remove(namespace);
    }
}
