package com.chat.gateway.metrics;

import com.chat.gateway.service.GatewayService;
import com.chat.gateway.websocket.ChatWebSocketHandler;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class MetricsController {
    private final GatewayService gatewayService;
    private final ChatWebSocketHandler wsHandler;

    public MetricsController(GatewayService gatewayService, ChatWebSocketHandler wsHandler) {
        this.gatewayService = gatewayService;
        this.wsHandler = wsHandler;
    }

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        long reqs = gatewayService.totalRequests.get();
        long avgLatency = reqs > 0 ? gatewayService.totalLatencyMs.get() / reqs : 0;
        return Map.of(
            "activeWebSocketClients", wsHandler.activeSessions.size(),
            "totalRequestsProcessed", reqs,
            "droppedByBackpressure", gatewayService.droppedRequests.get(),
            "droppedBySlowClient", gatewayService.droppedBySlowClient.get(),
            "failedTimeouts", gatewayService.failedRequests.get(),
            "averageLatencyMs", avgLatency
        );
    }
}