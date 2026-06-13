const socket = new WebSocket('ws://localhost:8080/chat');
const chatBox = document.getElementById('chat');
const usernameInput = document.getElementById('username');
const messageInput = document.getElementById('message');

socket.onmessage = function(event) {
    const rawData = event.data;
    const p = document.createElement('p');
    
    if (rawData.includes("System Alert")) {
        p.style.color = 'red';
        p.style.fontWeight = 'bold';
    } else {
        p.style.color = '#333';
    }
    
    p.textContent = rawData;
    chatBox.appendChild(p);
    chatBox.scrollTop = chatBox.scrollHeight;
};

socket.onclose = function() {
    const p = document.createElement('p');
    p.style.color = 'red';
    p.textContent = "Disconnected from Gateway.";
    chatBox.appendChild(p);
};

function sendMessage() {
    const user = usernameInput.value.trim() || "Anonymous";
    const msg = messageInput.value.trim();
    if (msg !== "") {
        socket.send(`${user}: ${msg}`);
        messageInput.value = '';
    }
}

function handleKeyPress(event) {
    if (event.key === 'Enter') sendMessage();
}

async function checkMetrics() {
    try {
        const res = await fetch('http://localhost:8080/metrics');
        const data = await res.json();
        document.getElementById('metrics-result').innerText = JSON.stringify(data, null, 2);
    } catch(e) {
        document.getElementById('metrics-result').innerText = "Metrics offline";
    }
}

async function runBenchmark() {
    try {
        document.getElementById('metrics-result').innerText = "Running benchmark on Worker...";
        const res = await fetch('http://localhost:8081/benchmark');
        const data = await res.json();
        document.getElementById('metrics-result').innerText = JSON.stringify(data, null, 2);
    } catch(e) {
        document.getElementById('metrics-result').innerText = "Worker service offline";
    }
}