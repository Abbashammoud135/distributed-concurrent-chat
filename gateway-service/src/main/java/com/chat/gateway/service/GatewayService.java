package com.chat.gateway.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GatewayService {
    // Metrics variables
    public final AtomicLong totalRequests = new AtomicLong(0);
    public final AtomicLong droppedRequests = new AtomicLong(0);
    public final AtomicLong failedRequests = new AtomicLong(0);
    public final AtomicLong totalLatencyMs = new AtomicLong(0);

    // Bounded Resources: Explicit Thread Pool with Bounded Queue
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final String workerUrl;

    public GatewayService(@Value("${worker.url:http://localhost:8091}") String workerUrl) {
        this.workerUrl = workerUrl;
        // Core: 10, Max: 50, Queue: 100. Overload drops messages (Backpressure).
        this.executor = new ThreadPoolExecutor(
                10, 50, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.httpClient = HttpClient.newHttpClient();
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("Initiating graceful shutdown...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public void processMessage(String message, ConcurrentHashMap<String, WebSocketSession> sessions) {
        try {
            executor.submit(() -> {
                totalRequests.incrementAndGet();
                long start = System.currentTimeMillis();

                // Distributed Network Boundary: Call Worker Service
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(workerUrl + "/process"))
                        .POST(HttpRequest.BodyPublishers.ofString(message))
                        .header("Content-Type", "text/plain")
                        .build();

                // Async Pipeline & Timeout/Fallback
                CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                
                future.orTimeout(3, TimeUnit.SECONDS)
                      .thenAccept(response -> {
                          long latency = System.currentTimeMillis() - start;
                          totalLatencyMs.addAndGet(latency);
                          broadcast(response.body(), sessions);
                      })
                      .exceptionally(ex -> {
                          failedRequests.incrementAndGet();
                          System.err.println("Worker timeout or failure: " + ex.getMessage());
                          // Fallback path
                          broadcast("[System Alert] Worker service offline or busy. Your message was: " + message, sessions);
                          return null;
                      });
            });
        } catch (RejectedExecutionException e) {
            // Overload behavior: Queue is full, thread pool is maxed.
            droppedRequests.incrementAndGet();
            System.err.println("Gateway Overload: Message dropped due to backpressure.");
        }
    }

    private void broadcast(String payload, ConcurrentHashMap<String, WebSocketSession> sessions) {
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try { session.sendMessage(textMessage); } 
                catch (IOException ignored) {}
            }
        }
    }
}