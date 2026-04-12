const videoStream = document.getElementById('videoStream');
const noSignal = document.getElementById('noSignal');
const reconnectingOverlay = document.getElementById('reconnectingOverlay');
const peopleCount = document.getElementById('peopleCount');
const statusBadge = document.getElementById('statusBadge');
const statusIndicator = document.getElementById('statusIndicator');
const statusLabel = document.getElementById('statusLabel');
const timestampDisplay = document.getElementById('timestampDisplay');
const latencyCalc = document.getElementById('latencyCalc');

const densityCard = document.getElementById('densityCard');
const alertBox = document.getElementById('alertBox');
const alertIcon = document.getElementById('alertIcon');
const alertTitle = document.getElementById('alertTitle');
const alertMessage = document.getElementById('alertMessage');
const statusText = document.getElementById('statusText');
const statusDot = document.getElementById('statusDot');
const alertSound = document.getElementById('alertSound');

let ws;
let lastFrameTime = Date.now();
let checkConnectionInterval;
let redStateStartTime = 0;
let isAlertActive = false;

const STORAGE_KEY = 'crowdpulse_server_host';

function applyServerUrl() {
    const input = document.getElementById('serverUrlInput').value.trim();
    if (!input) return;
    // Strip any protocol prefix the user may have typed
    const host = input.replace(/^wss?:\/\//, '').replace(/^https?:\/\//, '');
    localStorage.setItem(STORAGE_KEY, host);
    if (ws) ws.close();
    initWebSocket(host);
}

function initWebSocket(host) {
    if (!host) {
        statusText.textContent = 'Enter server URL above';
        statusDot.className = 'w-3 h-3 rounded-full bg-slate-400';
        return;
    }
    // Use wss:// for any remote host, ws:// only for localhost / 10.0.x
    const isLocal = host.startsWith('localhost') || host.startsWith('127.') || host.startsWith('10.0.');
    const protocol = isLocal ? 'ws' : 'wss';
    const wsUrl = `${protocol}://${host}/ws/dashboard`;

    statusText.textContent = 'Connecting...';
    statusDot.className = 'w-3 h-3 rounded-full bg-amber-400 animate-pulse';

    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        statusText.textContent = 'Connected';
        statusDot.className = 'w-3 h-3 rounded-full bg-emerald-500 animate-pulse';
        clearInterval(checkConnectionInterval);
        checkConnectionInterval = setInterval(checkFallbackState, 1000);
    };

    ws.onclose = () => {
        statusText.textContent = 'Disconnected';
        statusDot.className = 'w-3 h-3 rounded-full bg-red-500';
        // Auto-retry after 4 seconds
        setTimeout(() => initWebSocket(host), 4000);
    };

    ws.onerror = () => {
        statusText.textContent = 'Error';
        statusDot.className = 'w-3 h-3 rounded-full bg-red-500';
    };

    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        updateDashboard(data);
    };
}

function updateDashboard(data) {
    lastFrameTime = Date.now();
    reconnectingOverlay.classList.replace('opacity-100', 'opacity-0');
    reconnectingOverlay.classList.add('pointer-events-none');
    
    // Update Video
    if (data.image_b64) {
        videoStream.src = "data:image/jpeg;base64," + data.image_b64;
        videoStream.classList.remove('hidden');
        noSignal.classList.add('hidden');
    }

    // Update Count
    peopleCount.textContent = data.count;

    // Time
    const d = new Date();
    timestampDisplay.textContent = d.toLocaleTimeString();

    // Update Density Logic
    applyStatusColor(data.status);
    
    // Alert System Logic (> 10s Red)
    if (data.status === "RED") {
        if (redStateStartTime === 0) redStateStartTime = Date.now();
        
        const duration = (Date.now() - redStateStartTime) / 1000;
        if (duration > 10 && !isAlertActive) {
            triggerAlert();
        }
    } else {
        redStateStartTime = 0;
        if (isAlertActive) clearAlert();
    }
}

function applyStatusColor(status) {
    statusLabel.textContent = status;
    // reset classes
    statusBadge.className = "inline-flex w-full justify-center items-center gap-2 px-4 py-3 rounded-xl font-bold transition-colors duration-300";
    densityCard.className = "glass-panel rounded-2xl p-6 transition-all duration-500 border-2";

    if (status === "GREEN") {
        statusIndicator.className = "w-2.5 h-2.5 rounded-full bg-emerald-500";
        statusBadge.classList.add('bg-emerald-100', 'text-emerald-700');
        densityCard.classList.add('border-emerald-200');
    } else if (status === "YELLOW") {
        statusIndicator.className = "w-2.5 h-2.5 rounded-full bg-amber-500 animate-pulse";
        statusBadge.classList.add('bg-amber-100', 'text-amber-700');
        densityCard.classList.add('border-amber-200');
    } else if (status === "RED") {
        statusIndicator.className = "w-2.5 h-2.5 rounded-full bg-red-600 animate-ping";
        statusBadge.classList.add('bg-red-100', 'text-red-700');
        densityCard.classList.add('border-red-300', 'shadow-lg', 'shadow-red-500/20');
    }
}

function triggerAlert() {
    isAlertActive = true;
    alertBox.className = "glass-panel rounded-2xl p-6 border-l-4 border-red-500 opacity-100 transition-all duration-300 pulse-red bg-red-50";
    alertIcon.className = "p-2 rounded-lg bg-red-100 text-red-600 animate-bounce";
    alertIcon.innerHTML = `<svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>`;
    alertTitle.textContent = "CRITICAL SERVER ALERT";
    alertTitle.classList.add('text-red-700');
    alertMessage.textContent = "High density threshold exceeded for >10 seconds. Dispatching security.";
    alertMessage.classList.replace('text-slate-500', 'text-red-600');
    
    // Play sound (Requires user to have clicked on the page first due to browser policies)
    try {
        alertSound.play().catch(e => console.log("Sound blocked by browser policy"));
    } catch(e) {}
}

function clearAlert() {
    isAlertActive = false;
    alertBox.className = "glass-panel rounded-2xl p-6 border-l-4 border-slate-300 opacity-50 transition-all duration-300";
    alertIcon.className = "p-2 rounded-lg bg-slate-100 text-slate-400";
    alertIcon.innerHTML = `<svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>`;
    alertTitle.textContent = "System Monitoring";
    alertTitle.classList.remove('text-red-700');
    alertMessage.textContent = "Density stabilized.";
    alertMessage.classList.replace('text-red-600', 'text-slate-500');
}

function checkFallbackState() {
    const timeSinceLastFrame = Date.now() - lastFrameTime;
    
    // Latency text
    latencyCalc.textContent = `${Math.floor(timeSinceLastFrame / 1000)}s ago`;

    // Case 2: Network Lost -> Trigger Reconnecting Fallback if no frame > 3s
    if (timeSinceLastFrame > 3000) {
        reconnectingOverlay.classList.replace('opacity-0', 'opacity-100');
        reconnectingOverlay.classList.remove('pointer-events-none');
    }
}

// On load: restore saved server host and auto-connect
const savedHost = localStorage.getItem(STORAGE_KEY);
if (savedHost) {
    document.getElementById('serverUrlInput').value = savedHost;
    initWebSocket(savedHost);
}
