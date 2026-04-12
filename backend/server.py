import asyncio
import base64
import json
import logging
import os
from typing import List, Dict

import cv2
import numpy as np
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from ultralytics import YOLO

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("CrowdPulse")

app = FastAPI(title="CrowdPulse AI Backend")

# Allow Web UI to fetch APIs if needed
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Load lightweight YOLO model
logger.info("Loading YOLO model...")
model = YOLO("yolov8n.pt")  # Auto-downloads if not present
logger.info("YOLO model loaded.")

class ConnectionManager:
    def __init__(self):
        self.camera_connections: List[WebSocket] = []
        self.dashboard_connections: List[WebSocket] = []

    async def connect_camera(self, websocket: WebSocket):
        await websocket.accept()
        self.camera_connections.append(websocket)
        logger.info(f"Camera connected. Total cams: {len(self.camera_connections)}")

    def disconnect_camera(self, websocket: WebSocket):
        if websocket in self.camera_connections:
            self.camera_connections.remove(websocket)
            logger.info("Camera disconnected.")

    async def connect_dashboard(self, websocket: WebSocket):
        await websocket.accept()
        self.dashboard_connections.append(websocket)
        logger.info(f"Dashboard connected. Total dashboards: {len(self.dashboard_connections)}")

    def disconnect_dashboard(self, websocket: WebSocket):
        if websocket in self.dashboard_connections:
            self.dashboard_connections.remove(websocket)
            logger.info("Dashboard disconnected.")

    async def broadcast_to_dashboards(self, data: str):
        for connection in self.dashboard_connections:
            try:
                await connection.send_text(data)
            except Exception as e:
                logger.error(f"Error sending to dashboard: {e}")

manager = ConnectionManager()

def process_frame(frame_bytes: bytes) -> Dict:
    """
    Decodes the image, runs YOLO inference, counts people,
    draws bounding boxes, and returns base64 image + metadata.
    """
    # 1. Decode JPEG bytes to OpenCV Image
    np_arr = np.frombuffer(frame_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    
    if img is None:
        raise ValueError("Failed to decode image")

    # 2. Run YOLO inference
    # classes=0 filters for 'person' class only
    results = model(img, classes=0, verbose=False)
    
    count = 0
    annotated_img = img.copy()

    if len(results) > 0:
        result = results[0]
        boxes = result.boxes
        count = len(boxes)
        
        # Plot boxes manually for custom styling or use ultralytics plotter
        annotated_img = result.plot()

    # 3. Density Logic
    if count <= 5:
        status = "GREEN"
    elif count <= 15:
        status = "YELLOW"
    else:
        status = "RED"

    # 4. Encode back to Base64 for the Web Dashboard
    _, encoded_img = cv2.imencode(".jpg", annotated_img, [cv2.IMWRITE_JPEG_QUALITY, 60])
    b64_image = base64.b64encode(encoded_img).decode("utf-8")

    return {
        "status": status,
        "count": count,
        "image_b64": b64_image,
        "timestamp": asyncio.get_event_loop().time()
    }


@app.websocket("/ws/camera")
async def websocket_camera(websocket: WebSocket):
    """
    Endpoint for the Mobile/Camera App to stream raw JPEG frames natively.
    """
    await manager.connect_camera(websocket)
    try:
        while True:
            # Receive byte array from mobile app Native WebSocket
            data = await websocket.receive_bytes()
            
            try:
                # Process the frame via AI
                result_payload = process_frame(data)
                
                # Broadcast the inference result to all open web dashboards
                await manager.broadcast_to_dashboards(json.dumps(result_payload))
                
            except Exception as e:
                logger.error(f"Inference error: {e}")
                
    except WebSocketDisconnect:
        manager.disconnect_camera(websocket)


@app.websocket("/ws/dashboard")
async def websocket_dashboard(websocket: WebSocket):
    """
    Endpoint for Web Apps to receive processed streams and alerts.
    """
    await manager.connect_dashboard(websocket)
    try:
        while True:
            # We just keep connection open, dashboard mostly listens
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect_dashboard(websocket)

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("server:app", host="0.0.0.0", port=port)
