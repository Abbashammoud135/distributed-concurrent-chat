package com.chat.gateway.service;

import com.chat.gateway.websocket.BoundedSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GatewayService {
    // Metrics variables
    public final AtomicLong totalRequests = new AtomicLong(0);
    public final AtomicLong droppedRequests = new AtomicLong(0);
    public final AtomicLong droppedBySlowClient = new AtomicLong(0);
    public final AtomicLong failedRequests = new AtomicLong(0);
    public final AtomicLong totalLatencyMs = new AtomicLong(0);

    // Bounded Resources: Explicit Thread Pools
    private final ExecutorService executor;
    private final ExecutorService senderExecutor;
    private final ConcurrentHashMap<String, ExecutorService> channelExecutors = new ConcurrentHashMap<>();
    
    private final HttpClient httpClient;
    private final String workerUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GatewayService(@Value("${worker.url:http://localhost:8091}") String workerUrl) {
        this.workerUrl = workerUrl;
        
        // Input processing pool: Core: 10, Max: 50, Queue: 100. Overload drops messages (Backpressure).
        this.executor = new ThreadPoolExecutor(
                10, 50, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy()
        );
        
        // Outbound send pool for slow client WebSocket queues. Core: 10, Max: 30, Queue: 500.
        this.senderExecutor = new ThreadPoolExecutor(
                10, 30, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.httpClient = HttpClient.newHttpClient();
    }

    public ExecutorService getSenderExecutor() {
        return senderExecutor;
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("Initiating graceful shutdown...");
        executor.shutdown();
        senderExecutor.shutdown();
        for (ExecutorService channelExec : channelExecutors.values()) {
            channelExec.shutdown();
        }
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!senderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                senderExecutor.shutdownNow();
            }
            for (ExecutorService channelExec : channelExecutors.values()) {
                if (!channelExec.awaitTermination(5, TimeUnit.SECONDS)) {
                    channelExec.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            senderExecutor.shutdownNow();
            for (ExecutorService channelExec : channelExecutors.values()) {
                channelExec.shutdownNow();
            }
        }
    }

    public void processMessage(String messageText, ConcurrentHashMap<String, BoundedSession> sessions) {
        try {
            // Task submitted to the main thread pool for parsing and routing
            executor.submit(() -> {
                String channel = "#general";
                String sender = "Anonymous";
                String text = messageText;

                try {
                    JsonNode node = objectMapper.readTree(messageText);
                    if (node.has("channel")) {
                        channel = node.get("channel").asText();
                    }
                    if (node.has("sender")) {
                        sender = node.get("sender").asText();
                    }
                    if (node.has("text")) {
                        text = node.get("text").asText();
                    }
                } catch (Exception e) {
                    // Fallback to legacy format parsing if not a JSON payload
                    if (messageText.contains(":")) {
                        int colonIndex = messageText.indexOf(":");
                        sender = messageText.substring(0, colonIndex).trim();
                        text = messageText.substring(colonIndex + 1).trim();
                    }
                }

                final String finalChannel = channel;
                final String finalSender = sender;
                final String finalText = text;

                // Route message processing to a channel-specific single-threaded executor
                // to guarantee message ordering for that channel without global locks.
                ExecutorService channelExecutor = channelExecutors.computeIfAbsent(finalChannel,
                        k -> Executors.newSingleThreadExecutor(r -> {
                            Thread t = new Thread(r, "channel-" + k + "-worker");
                            t.setDaemon(true);
                            return t;
                        })
                );

                channelExecutor.submit(() -> {
                    totalRequests.incrementAndGet();
                    long start = System.currentTimeMillis();

                    // Prepare HTTP request with a 3-second timeout
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(workerUrl + "/process"))
                            .timeout(Duration.ofSeconds(3))
                            .POST(HttpRequest.BodyPublishers.ofString(finalText))
                            .header("Content-Type", "text/plain")
                            .build();

                    try {
                        // Synchronous HTTP call block (runs on the channel's single worker thread)
                        // to preserve exact order before the next message in this channel is processed.
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        long latency = System.currentTimeMillis() - start;
                        totalLatencyMs.addAndGet(latency);

                        String processedText = response.body();
                        broadcast(finalChannel, finalSender, processedText, sessions);
                    } catch (Exception ex) {
                        failedRequests.incrementAndGet();
                        System.err.println("Worker timeout or failure on channel " + finalChannel + ": " + ex.getMessage());
                        // Fallback path: broadcast a system warning
                        String fallbackText = "[System Alert] Worker service offline or busy. Your message was: " + finalText;
                        broadcast(finalChannel, "System", fallbackText, sessions);
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            // Overload behavior: Queue is full, thread pool is maxed.
            droppedRequests.incrementAndGet();
            System.err.println("Gateway Overload: Message dropped due to backpressure.");
        }
    }

    public void broadcast(String channel, String sender, String text, ConcurrentHashMap<String, BoundedSession> sessions) {
        try {
            Map<String, String> payloadMap = Map.of(
                    "channel", channel,
                    "sender", sender,
                    "text", text
            );
            String payload = objectMapper.writeValueAsString(payloadMap);
            TextMessage textMessage = new TextMessage(payload);

            for (BoundedSession session : sessions.values()) {
                if (session.isOpen()) {
                    boolean success = session.send(textMessage);
                    if (!success) {
                        droppedBySlowClient.incrementAndGet();
                        System.err.println("Slow client detected: Message dropped for session " + session.getId());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error serializing broadcast message: " + e.getMessage());
        }
    }
}