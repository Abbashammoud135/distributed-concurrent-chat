let socket;
let autoRefreshInterval = null;
let currentChannel = '#general';

const chatBox = document.getElementById('chat');
const usernameInput = document.getElementById('username');
const messageInput = document.getElementById('message');

// Maintain client-side message histories per channel
const messageHistory = {
    '#general': [],
    '#random': [],
    '#support': []
};

function formatTime() {
    const now = new Date();
    return now.toTimeString().split(' ')[0];
}

function connectWebSocket() {
    const statusDot = document.getElementById('connection-dot');
    const statusText = document.getElementById('connection-text');
    const chatSubtitle = document.getElementById('chat-subtitle');

    statusDot.className = 'status-dot connecting';
    statusText.innerText = 'Connecting...';
    chatSubtitle.innerText = 'Connecting to ws://localhost:8090/chat...';

    // Establish WebSocket Connection to Gateway
    socket = new WebSocket('ws://localhost:8090/chat');

    socket.onopen = function () {
        statusDot.className = 'status-dot connected';
        statusText.innerText = 'Connected';
        chatSubtitle.innerText = 'Active Gateway Tunnel: ws://localhost:8090/chat';
        
        // Populate initial metrics immediately on connection
        checkMetrics();
    };

    socket.onmessage = function (event) {
        const rawData = event.data;
        appendMessage(rawData);
    };

    socket.onclose = function () {
        statusDot.className = 'status-dot disconnected';
        statusText.innerText = 'Disconnected';
        chatSubtitle.innerText = 'Offline: Gateway connection closed.';
        
        appendSystemMessage("Disconnected from Gateway. Attempting to reconnect in 5 seconds...", "error");
        
        // Reconnect loop every 5s
        setTimeout(connectWebSocket, 5000);
    };

    socket.onerror = function () {
        statusDot.className = 'status-dot disconnected';
        statusText.innerText = 'Error';
    };
}

function switchChannel(channel) {
    if (channel === currentChannel) return;
    currentChannel = channel;
    
    // Update active channel tab UI
    const buttons = document.querySelectorAll('.btn-channel');
    buttons.forEach(btn => {
        if (btn.innerText.trim() === channel) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });

    // Clear chat display area
    chatBox.innerHTML = '';

    // Append system welcome text
    appendSystemMessage(`Joined channel ${channel}`, "info");

    // Render historical messages for the switched channel
    if (messageHistory[channel]) {
        messageHistory[channel].forEach(msg => {
            renderMessage(msg.sender, msg.text);
        });
    }
}

function appendMessage(rawData) {
    let channel = '#general';
    let sender = 'System';
    let text = rawData;

    try {
        const data = JSON.parse(rawData);
        channel = data.channel || '#general';
        sender = data.sender || 'System';
        text = data.text || '';
    } catch (e) {
        // Fallback for raw legacy messages
        if (rawData.startsWith("[System Alert]")) {
            sender = 'System';
            text = rawData;
        } else {
            const match = rawData.match(/^([^:]+):\s*(.*)$/);
            if (match) {
                sender = match[1].trim();
                text = match[2].trim();
            }
        }
    }

    // Save to client history for switching tabs
    if (messageHistory[channel]) {
        messageHistory[channel].push({ sender, text });
    }

    // Render only if it matches current active channel view
    if (channel === currentChannel) {
        renderMessage(sender, text);
    }
}

function renderMessage(sender, text) {
    if (sender === 'System' || sender === 'system') {
        const isErrorAlert = text.includes('[System Alert]');
        appendSystemMessage(text, isErrorAlert ? 'error' : 'info');
        return;
    }

    let isProcessed = false;
    let messageBody = text;

    // Check if worker processed badge is present
    if (messageBody.endsWith("[Processed by Worker]")) {
        isProcessed = true;
        messageBody = messageBody.replace("[Processed by Worker]", "").trim();
    }

    const currentUsername = usernameInput.value.trim() || "Anonymous";
    const isSelf = sender.toLowerCase() === currentUsername.toLowerCase();

    // Create bubble nodes
    const bubble = document.createElement('div');
    bubble.className = `message-bubble ${isSelf ? 'bubble-self' : 'bubble-other'}`;

    const meta = document.createElement('div');
    meta.className = 'bubble-meta';
    
    const senderSpan = document.createElement('span');
    senderSpan.className = 'bubble-sender';
    senderSpan.innerText = sender;
    
    if (isProcessed) {
        const badge = document.createElement('span');
        badge.className = 'processed-badge';
        badge.innerText = 'Worker';
        senderSpan.appendChild(badge);
    }
    
    const timeSpan = document.createElement('span');
    timeSpan.className = 'bubble-time';
    timeSpan.innerText = formatTime();
    
    meta.appendChild(senderSpan);
    meta.appendChild(timeSpan);

    const textDiv = document.createElement('div');
    textDiv.className = 'bubble-text';
    textDiv.innerText = messageBody;

    bubble.appendChild(meta);
    bubble.appendChild(textDiv);
    
    chatBox.appendChild(bubble);
    chatBox.scrollTop = chatBox.scrollHeight;
}

function appendSystemMessage(text, type) {
    const container = document.createElement('div');
    container.className = `system-message ${type === 'error' ? 'error-banner' : 'info-banner'}`;
    
    const p = document.createElement('p');
    p.innerText = text;
    container.appendChild(p);
    
    chatBox.appendChild(container);
    chatBox.scrollTop = chatBox.scrollHeight;
}

function sendMessage() {
    const user = usernameInput.value.trim() || "Anonymous";
    const msg = messageInput.value.trim();
    if (msg !== "" && socket && socket.readyState === WebSocket.OPEN) {
        // Send as a structured JSON object
        const payload = JSON.stringify({
            channel: currentChannel,
            sender: user,
            text: msg
        });
        socket.send(payload);
        messageInput.value = '';
    } else if (!socket || socket.readyState !== WebSocket.OPEN) {
        appendSystemMessage("Cannot send message: WebSocket connection is offline.", "error");
    }
}

function handleKeyPress(event) {
    if (event.key === 'Enter') sendMessage();
}

async function checkMetrics() {
    const rawPre = document.getElementById('metrics-result');
    const metricClients = document.getElementById('metric-active-clients');
    const metricProcessed = document.getElementById('metric-processed');
    const metricLatency = document.getElementById('metric-latency');
    const metricDropped = document.getElementById('metric-dropped');
    const metricSlowDropped = document.getElementById('metric-slow-dropped');
    const metricFailed = document.getElementById('metric-failed');
    
    const cardActive = document.getElementById('card-active-clients');
    const cardDropped = document.getElementById('card-dropped');
    const cardSlowDropped = document.getElementById('card-slow-dropped');
    const cardFailed = document.getElementById('card-failed');

    try {
        const res = await fetch('http://localhost:8090/metrics');
        if (!res.ok) throw new Error("HTTP error " + res.status);
        const data = await res.json();
        
        // Update raw telemetry pre block
        rawPre.innerText = JSON.stringify(data, null, 2);

        // Update parsed dashboard panels
        const activeCount = data.activeWebSocketClients || 0;
        metricClients.innerText = activeCount;
        if (activeCount > 0) {
            cardActive.classList.add('active-users-glow');
        } else {
            cardActive.classList.remove('active-users-glow');
        }

        metricProcessed.innerText = data.totalRequestsProcessed || 0;
        
        const avgLat = data.averageLatencyMs || 0;
        metricLatency.innerHTML = `${avgLat}<span class="unit">ms</span>`;

        const droppedCount = data.droppedByBackpressure || 0;
        metricDropped.innerText = droppedCount;
        if (droppedCount > 0) {
            cardDropped.classList.add('active');
        } else {
            cardDropped.classList.remove('active');
        }

        const slowDroppedCount = data.droppedBySlowClient || 0;
        metricSlowDropped.innerText = slowDroppedCount;
        if (slowDroppedCount > 0) {
            cardSlowDropped.classList.add('active');
        } else {
            cardSlowDropped.classList.remove('active');
        }

        const failedCount = data.failedTimeouts || 0;
        metricFailed.innerText = failedCount;
        if (failedCount > 0) {
            cardFailed.classList.add('active');
        } else {
            cardFailed.classList.remove('active');
        }

    } catch (e) {
        rawPre.innerText = "Metrics service offline / connection failed";
        metricClients.innerText = "-";
        metricProcessed.innerText = "-";
        metricLatency.innerHTML = `-<span class="unit">ms</span>`;
        metricDropped.innerText = "-";
        metricSlowDropped.innerText = "-";
        metricFailed.innerText = "-";
        cardActive.classList.remove('active-users-glow');
        cardDropped.classList.remove('active');
        cardSlowDropped.classList.remove('active');
        cardFailed.classList.remove('active');
    }
}

function toggleAutoRefresh(checkbox) {
    if (checkbox.checked) {
        checkMetrics();
        autoRefreshInterval = setInterval(checkMetrics, 3000);
    } else {
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
        }
    }
}

async function runBenchmark() {
    const resultsContainer = document.getElementById('benchmark-results');
    const speedupVal = document.getElementById('bench-speedup');
    const seqTimeText = document.getElementById('bench-seq-time');
    const parTimeText = document.getElementById('bench-par-time');
    const seqBar = document.getElementById('bench-seq-bar');
    const parBar = document.getElementById('bench-par-bar');
    const rawPre = document.getElementById('metrics-result');

    // Show visual panel and load indicators
    resultsContainer.style.display = 'block';
    speedupVal.innerText = '...';
    seqTimeText.innerText = 'Calculating...';
    parTimeText.innerText = 'Calculating...';
    seqBar.style.width = '0%';
    parBar.style.width = '0%';

    try {
        rawPre.innerText = "Running concurrent/sequential CPU benchmark on Worker service...";
        const res = await fetch('http://localhost:8091/benchmark');
        if (!res.ok) throw new Error("HTTP error " + res.status);
        const data = await res.json();
        
        rawPre.innerText = JSON.stringify(data, null, 2);

        const seq = data.sequentialTimeMs;
        const par = data.parallelTimeMs;
        
        seqTimeText.innerText = `${seq} ms`;
        parTimeText.innerText = `${par} ms`;

        // Calculate and show speedup ratio
        if (par > 0) {
            const speedup = (seq / par).toFixed(1);
            speedupVal.innerText = `${speedup}x`;
        } else {
            speedupVal.innerText = 'N/A';
        }

        // Draw visual bars relative to max execution time
        const max = Math.max(seq, par);
        if (max > 0) {
            const seqPercent = (seq / max) * 100;
            const parPercent = (par / max) * 100;
            // Delay slightly to trigger CSS transition animation
            setTimeout(() => {
                seqBar.style.width = `${seqPercent}%`;
                parBar.style.width = `${parPercent}%`;
            }, 50);
        }

    } catch (e) {
        rawPre.innerText = "Benchmark failed: Worker service is offline";
        seqTimeText.innerText = "Offline";
        parTimeText.innerText = "Offline";
        speedupVal.innerText = "Error";
    }
}

function toggleTheme() {
    const body = document.body;
    const darkIcon = document.querySelector('.theme-icon-dark');
    const lightIcon = document.querySelector('.theme-icon-light');
    
    body.classList.toggle('light-theme');
    
    const isLight = body.classList.contains('light-theme');
    if (isLight) {
        darkIcon.style.display = 'none';
        lightIcon.style.display = 'inline-block';
        localStorage.setItem('theme', 'light');
    } else {
        darkIcon.style.display = 'inline-block';
        lightIcon.style.display = 'none';
        localStorage.setItem('theme', 'dark');
    }
}

// Initial Connection & Theme Trigger
window.onload = function() {
    // Check saved theme preference
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'light') {
        document.body.classList.add('light-theme');
        document.querySelector('.theme-icon-dark').style.display = 'none';
        document.querySelector('.theme-icon-light').style.display = 'inline-block';
    }
    
    // Add channel message histories initial state
    appendSystemMessage("Joined channel #general", "info");
    
    connectWebSocket();
};