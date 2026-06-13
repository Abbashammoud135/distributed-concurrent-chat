# Topic A: Distributed Live Chat

This project fulfills all the advanced concurrency and distributed systems requirements outlined in the PDF.

## 🚀 Architecture Updates
1. **Gateway Service (Port 8080):** Handles WebSockets, connection limits, and Bounded Queues/Backpressure.
2. **Worker Service (Port 8081):** Simulates CPU-bound tasks and message processing over a Distributed Network Boundary.

## ✅ Fulfilled Requirements
* **Distributed Network Boundary:** Gateway communicates with Worker via Java 21 `HttpClient` REST calls.
* **Bounded Resources & Backpressure:** `ThreadPoolExecutor` has a `LinkedBlockingQueue(100)` and `AbortPolicy`.
* **Async Pipeline & Timeouts:** Uses `CompletableFuture.orTimeout(3s).exceptionally(...)` to handle worker failures gracefully.
* **CPU-Bound Benchmark:** Endpoint to compare Sequential vs Parallel stream processing.
* **Metrics:** Latency, dropped requests, active connections.
* **Graceful Shutdown:** Implemented via `@PreDestroy`.

## 🛠️ How to Run

1. **Run Worker Service (Backend Engine)**
   * Open `worker-service` folder in IntelliJ/VSCode.
   * Run `WorkerApplication.java`. (It will start on port 8081).

2. **Run Gateway Service (WebSocket API)**
   * Open `gateway-service` folder in IntelliJ/VSCode.
   * Run `GatewayApplication.java`. (It will start on port 8080).

3. **Run Frontend**
   * Open terminal in `frontend` folder.
   * Run `python -m http.server 8000`.
   * Open `http://localhost:8000` in multiple browser tabs.

## 💥 How to Inject Failures for the Presentation
* **Trigger a Timeout/Fallback:** Type `sleep` in your chat message. The worker will intentionally hang for 4 seconds, triggering the 3-second Gateway timeout, and you will see the red Fallback message!
* **Test Worker Offline:** Stop the `worker-service` in your IDE and try sending a message. The Gateway will gracefully catch the network error and send a fallback alert without crashing.
