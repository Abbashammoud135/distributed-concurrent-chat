package com.chat.gateway.websocket;

import com.chat.gateway.service.GatewayService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final GatewayService gatewayService;
    public final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(GatewayService gatewayService) { this.gatewayService = gatewayService; }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) { activeSessions.put(session.getId(), session); }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        gatewayService.processMessage(message.getPayload(), activeSessions);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { activeSessions.remove(session.getId()); }
}