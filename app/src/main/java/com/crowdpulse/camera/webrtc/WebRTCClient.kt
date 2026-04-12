package com.crowdpulse.camera.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString

class WebRTCClient(private val context: Context, private val onConnectionStateChanged: (Boolean) -> Unit) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    var isConnected = false
        private set

    // Default: emulator localhost. Override via setServerIp() for physical devices or cloud.
    private var serverHost = "10.0.2.2:8000"
    private var useSecure = false  // set to true for wss:// (cloud/Render)

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        connectWebSocket()
    }

    fun setServerIp(ip: String) {
        // ip can be "10.0.2.2" or "myapp.onrender.com" (no port for Render)
        val isLocal = ip.startsWith("10.0.") || ip.startsWith("192.168.") || ip.startsWith("localhost")
        serverHost = if (isLocal) "$ip:8000" else ip
        useSecure = !isLocal
        if (serverHost != "$ip:8000" || webSocket == null) {
            webSocket?.close(1000, "IP Changed")
            isConnected = false
            onConnectionStateChanged(false)
            connectWebSocket()
        }
    }

    private fun connectWebSocket() {
        val protocol = if (useSecure) "wss" else "ws"
        val url = "$protocol://$serverHost/ws/camera"
        Log.d("WebRTCClient", "Connecting to: $url")
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebRTCClient", "WebSocket Connected!")
                isConnected = true
                onConnectionStateChanged(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Not expecting messages from server right now
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                Log.d("WebRTCClient", "WebSocket Closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isConnected) {
                    isConnected = false
                    onConnectionStateChanged(false)
                    handleDisconnection()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebRTCClient", "WebSocket Error", t)
                if (isConnected || webSocket == this@WebRTCClient.webSocket) {
                    isConnected = false
                    onConnectionStateChanged(false)
                    handleDisconnection()
                }
            }
        })
    }

    private fun handleDisconnection() {
        scope.launch {
            Log.e("WebRTCClient", "Connection lost! Retrying every 3 seconds...")
            delay(3000)
            if (!isConnected) {
                Log.d("WebRTCClient", "Attempting reconnect...")
                connectWebSocket()
            }
        }
    }

    fun sendFrame(jpegBytes: ByteArray) {
        if (!isConnected) return
        
        // Use OkHttp WebSockets to push bytes directly
        val byteString = ByteString.of(*jpegBytes)
        val success = webSocket?.send(byteString) ?: false
        if (!success) {
            Log.d("WebRTCClient", "Warning: Frame dropped due to socket buffer full")
        }
    }

    fun release() {
        webSocket?.close(1000, "App Destroyed")
        scope.cancel()
    }
}
