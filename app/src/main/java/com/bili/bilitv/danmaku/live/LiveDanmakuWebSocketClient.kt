package com.bili.bilitv.danmaku.live

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.max
import com.bili.bilitv.BuildConfig

data class LiveDanmakuItem(
    val time: Long,
    val text: String,
    val color: Int,
    val userName: String
)

class LiveDanmakuWebSocketClient(
    private val roomId: Long,
    private val token: String,
    private val hostList: List<LiveHostItem>
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    
    private val _danmakuFlow = Channel<LiveDanmakuItem>(Channel.BUFFERED)
    val danmakuFlow = _danmakuFlow.receiveAsFlow()

    private var isConnected = false
    private var retryCount = 0

    fun connect() {
        scope.launch {
            internalConnect()
        }
    }

    private suspend fun internalConnect() {
        if (hostList.isEmpty()) return
        
        // Simple round-robin or first available
        val hostItem = hostList.firstOrNull() ?: return
        val url = "wss://${hostItem.host}:${hostItem.wss_port}/sub"
        
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (BuildConfig.DEBUG) {
                    Log.d("LiveDanmaku", "WebSocket Connected")
                }
                isConnected = true
                retryCount = 0
                sendAuth()
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Bilibili live uses binary frames mostly
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (BuildConfig.DEBUG) {
                    Log.d("LiveDanmaku", "WebSocket Closing: $reason")
                }
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (BuildConfig.DEBUG) {
                    Log.d("LiveDanmaku", "WebSocket Closed")
                }
                isConnected = false
                reconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("LiveDanmaku", "WebSocket Failure", t)
                isConnected = false
                reconnect()
            }
        })
    }

    private fun reconnect() {
        heartbeatJob?.cancel()
        if (retryCount < 5) {
            scope.launch {
                delay(3000)
                retryCount++
                if (BuildConfig.DEBUG) {
                    Log.d("LiveDanmaku", "Reconnecting... ($retryCount)")
                }
                internalConnect()
            }
        }
    }

    private fun sendAuth() {
        val authParams = buildJsonObject {
            put("uid", 0)
            put("roomid", roomId)
            put("protover", 3)
            put("platform", "web")
            put("type", 2)
            put("key", token)
        }.toString()

        val body = authParams.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(16)
        header.putInt(16 + body.size) // Total Size
        header.putShort(16) // Header Size
        header.putShort(1) // ProtoVer
        header.putInt(7) // Opcode: Auth
        header.putInt(1) // Sequence

        val packet = ByteBuffer.allocate(16 + body.size)
        packet.put(header.array())
        packet.put(body)
        
        webSocket?.send(packet.array().toByteString())
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                sendHeartbeat()
                delay(30000)
            }
        }
    }

    private fun sendHeartbeat() {
        val header = ByteBuffer.allocate(16)
        header.putInt(16) // Total Size (No body for heartbeat usually, or empty obj)
        header.putShort(16)
        header.putShort(1)
        header.putInt(2) // Opcode: Heartbeat
        header.putInt(1)
        
        // Bilibili heartbeat payload can be empty or string "[object Object]" bytes
        // Standard impl is just header with empty body or special bytes.
        // Documentation says Body: empty.
        
        webSocket?.send(header.array().toByteString())
    }

    private fun handleMessage(bytes: ByteArray) {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val totalLen = buffer.int
            val headerLen = buffer.short.toInt()
            val protoVer = buffer.short.toInt()
            val opcode = buffer.int
            val seq = buffer.int

            if (bytes.size < totalLen) return // Incomplete packet

            val bodyLen = totalLen - headerLen
            val body = ByteArray(bodyLen)
            System.arraycopy(bytes, headerLen, body, 0, bodyLen)

            when (protoVer) {
                0 -> { // Plain JSON
                    handlePacketBody(opcode, body)
                }
                2 -> { // Zlib
                    val inflater = Inflater()
                    inflater.setInput(body)
                    val bos = ByteArrayOutputStream()
                    val tmp = ByteArray(1024)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(tmp)
                        if (count == 0) break
                        bos.write(tmp, 0, count)
                    }
                    inflater.end()
                    // Recursive handle unzipped data (may contain multiple packets)
                    handleStream(bos.toByteArray())
                }
                3 -> { // Brotli
                    val bIn = BrotliInputStream(ByteArrayInputStream(body))
                    val bOut = ByteArrayOutputStream()
                    val tmp = ByteArray(1024)
                    var len: Int
                    while (bIn.read(tmp).also { len = it } != -1) {
                        bOut.write(tmp, 0, len)
                    }
                    handleStream(bOut.toByteArray())
                }
                else -> {
                    // Unknown compression
                }
            }
        } catch (e: Exception) {
            Log.e("LiveDanmaku", "Parse Error", e)
        }
    }

    private fun handleStream(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            if (bytes.size - offset < 16) break
            
            val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.BIG_ENDIAN)
            val len = buffer.int
            val headerLen = buffer.short.toInt()
            val ver = buffer.short.toInt()
            val op = buffer.int
            val seq = buffer.int
            
            val bodyLen = len - headerLen
            val body = ByteArray(bodyLen)
            System.arraycopy(bytes, offset + headerLen, body, 0, bodyLen)
            
            handlePacketBody(op, body)
            
            offset += len
        }
    }

    private fun handlePacketBody(opcode: Int, body: ByteArray) {
        if (opcode == 5) { // Command
            try {
                val jsonStr = String(body, Charsets.UTF_8)
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                val cmd = json["cmd"]?.jsonPrimitive?.content ?: return
                
                if (cmd.startsWith("DANMU_MSG")) {
                    val info = json["info"]?.jsonArray ?: return
                    val text = info[1].jsonPrimitive.content
                    val extra = info[0].jsonArray
                    val color = extra[3].jsonPrimitive.int
                    val userArr = info[2].jsonArray
                    val userName = userArr[1].jsonPrimitive.content
                    val timestamp = extra[4].jsonPrimitive.long
                    
                    val item = LiveDanmakuItem(timestamp, text, color, userName)
                    _danmakuFlow.trySend(item)
                }
            } catch (e: Exception) {
                // Log.e("LiveDanmaku", "JSON Parse Error", e)
            }
        }
    }

    fun close() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Normal Close")
        scope.cancel()
    }
}
