# CrowdPulse System Architecture Guide

Welcome to the definitive guide on the CrowdPulse ecosystem. This document provides an in-depth breakdown of how our native Android Application and Web Dashboard are constructed, and exactly how they interface in real-time to create a seamless, AI-powered crowd monitoring pipeline.

---

## 1. The CrowdPulse Android Application (The Edge Node)

**Primary Role:** Video capture, dynamic hardware framing, and data streaming.  
**Core Technologies:** Kotlin, Jetpack Compose, CameraX, OkHttp WebSockets.

The Android app acts as the "eyes" of the system. We chose native Android development over a web app because it grants us low-level control over the device’s camera hardware (locking focus, adjusting exposure, triggering the physical flash, and manipulating the byte stream). 

### How it Works Internally:
1. **CameraX Engine (`CameraDeviceManager.kt`)**: The app uses Google's CameraX library to lock onto the camera lens. It establishes a live `TextureView` on your screen and intercepts raw imaging bytes off the Android camera hardware layer without saving anything to disk.
2. **The Smart Controller (`SmartController.kt`)**: This is our custom logic orchestrator. To prevent device crashes caused by memory buffer overflows (when the camera grabs 60 frames per second but the network can't send them that fast), the Smart Controller acts as a gatekeeper. It smartly samples raw YUV data, mathematically compresses it into precise, lightweight JPEG bytes, and places it into an `AtomicBoolean` processing lock.
3. **The Socket Client (`WebRTCClient.kt`)**: Once a frame is compressed, it is handed directly to our custom OkHttp WebSocket client. Rather than heavy HTTP POST requests, OkHttp streams these binary blobs persistently over a single, lightning-fast TCP handshake.

---

## 2. The Web Dashboard (The Command Center)

**Primary Role:** Real-time data visualization, AI telemetry, and security alerts.  
**Core Technologies:** HTML5, TailwindCSS, Vanilla JavaScript (`app.js`), WebSocket API.

The Web Dashboard is the "face" of the system where security officers or event coordinators can actively monitor large gatherings. It is designed to be fully decoupled from the mobile app, meaning you can have 10 dashboards open simultaneously all reading from the same data stream.

### How it Works Internally:
1. **The Pulse Socket (`initWebSocket`)**: Immediately upon opening `index.html`, `app.js` runs out to the central backend at `ws://localhost:8000/ws/dashboard`. It essentially "subscribes" to the security channel.
2. **Decoupled Video Injection**: When the AI server finishes analyzing a frame, it passes down a massive JSON packet containing an encoded `Base64` image. The dashboard takes this raw string and injects it straight into the DOM (`<img src="data:image/jpeg;base64,...">`). This bypasses the need to ever load or save actual video files, resulting in 0-latency playback.
3. **The State Machine Fallback**: The app runs a `setInterval` loop polling exactly 1000ms at a time. If the time since the last registered frame exceeds 3 seconds, the dashboard overrides the DOM and triggers a strict "Network Lost" overlay (Case 2 Fallback). If the "RED" density state persists for 10 straight seconds, it overrides the DOM again with a strict, glowing red security alert (Case 3 Alert).

---

## 3. The Integration Pipeline (How They Work Together)

The magic of CrowdPulse happens in the **Pipeline**. Neither the Android App nor the Web Dashboard knows the other exists. They are fully abstracted by the central brain: **The Python FastAPI Server**.

Here is the exact step-by-step lifecycle of a single frame traveling through the architecture:

### Step A: Capture & Transmit
1. A physical crowd stands in front of the phone.
2. `CameraX` grabs a frame array and passes it to the `SmartController`.
3. `ImageUtils` shrinks this bitmap into a 60%-quality JPEG array (`toByteArray()`).
4. The `OkHttp WebSocket` instantly flashes this byte fragment over the Local Area Network to the Python Backend.

### Step B: The AI Brain (Backend processing)
1. Python's `FastAPI` server receives the raw byte array on its `/ws/camera` endpoint.
2. The server converts the bytes into a **NumPy Array**.
3. It passes this array into the **Ultralytics YOLOv8 Neural Network**.
4. The AI identifies specifically the "Person" class (Class 0), counts the bounding boxes, and draws green squares over the image where people are detected.
5. The logic engine calculates the density scale. *(Count <= 5 is GREEN. Count <= 15 is YELLOW. Higher is RED).*
6. The server re-zips the bounding box image into a Base64 string. 

### Step C: Broadcast & Visualize
1. The server serializes the Count, the Status, and the Base64 image into a `JSON Payload`.
2. It loops through all connected Web Dashboard instances (`/ws/dashboard`) and instantly fires the JSON down to them.
3. `app.js` catches the text, parses it, flips the dynamic logic colors, and replaces the underlying HTML image tag with the fully processed AI frame.

Because we chose WebSockets over REST APIs, this entire three-step `(Phone -> AI Server -> Desktop Dashboard)` process occurs in practically native real-time without the brutal HTTP teardown overhead.
