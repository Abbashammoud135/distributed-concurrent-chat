package com.chat.gateway.websocket;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BoundedSession wraps a Spring WebSocketSession to provide bounded resource usage
 * and backpressure for slow clients. Instead of blocking the broadcasting thread
 * during session.sendMessage(), messages are offered to a bounded queue and sent
 * asynchronously by a dedicated thread pool.
 */
public class BoundedSession {
    private final WebSocketSession session;
    private final BlockingQueue<TextMessage> queue;
    private final ExecutorService senderExecutor;
    private final AtomicBoolean isSending = new AtomicBoolean(false);

    public BoundedSession(WebSocketSession session, ExecutorService senderExecutor, int capacity) {
        this.session = session;
        this.senderExecutor = senderExecutor;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public WebSocketSession getSession() {
        return session;
    }

    public String getId() {
        return session.getId();
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    /**
     * Enqueues a message to be sent asynchronously to this client.
     * If the queue is full, the message is dropped to protect the system.
     *
     * @param message the TextMessage to send
     * @return true if enqueued, false if dropped (slow client)
     */
    public boolean send(TextMessage message) {
        boolean offered = queue.offer(message);
        if (!offered) {
            // Queue is full! Backpressure triggered.
            return false;
        }
        tryScheduleSend();
        return true;
    }

    private void tryScheduleSend() {
        if (isSending.compareAndSet(false, true)) {
            senderExecutor.submit(this::processQueue);
        }
    }

    private void processQueue() {
        try {
            TextMessage msg;
            while ((msg = queue.poll()) != null) {
                if (session.isOpen()) {
                    session.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            System.err.println("Error sending message to session " + getId() + ", closing session: " + e.getMessage());
            try {
                session.close();
            } catch (IOException ignored) {}
        } finally {
            isSending.set(false);
            // Double check to prevent race where a message was offered right after poll()
            // returned null but before isSending was set to false.
            if (!queue.isEmpty()) {
                tryScheduleSend();
            }
        }
    }
}
