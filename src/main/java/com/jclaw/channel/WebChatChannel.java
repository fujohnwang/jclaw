package com.jclaw.channel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Web-based chat channel — serves a browser UI and exposes a REST API.
 * Uses JDK built-in HttpServer, no extra dependencies.
 */
public final class WebChatChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(WebChatChannel.class);

    private final int port;
    private final String adminToken;
    private final Runnable shutdownHook;
    private HttpServer server;
    private MessageHandler handler;
    private volatile Thread blockedThread;

    public WebChatChannel(int port, String adminToken, Runnable shutdownHook) {
        this.port = port;
        this.adminToken = adminToken;
        this.shutdownHook = shutdownHook;
    }

    @Override
    public String id() {
        return "webchat";
    }

    @Override
    public void start(MessageHandler handler) {
        this.handler = handler;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", this::handlePage);
            server.createContext("/api/chat", this::handleChat);
            server.createContext("/api/shutdown", this::handleShutdown);
            server.start();
            log.info("WebChat channel started on http://localhost:{}", port);
            System.out.println("WebChat channel started on http://localhost:" + port);

            // Block the calling thread; stop() will interrupt to unblock
            blockedThread = Thread.currentThread();
            Thread.currentThread().join();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start WebChat server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void send(String to, String message) {
        // WebChat is request-response based; replies are returned inline via handleChat.
        log.debug("WebChat send (no-op push): to={}, msg={}", to, message);
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(1);
            log.info("WebChat channel stopped");
        }
        if (blockedThread != null) {
            blockedThread.interrupt();
        }
    }

    // ── HTTP handlers ──────────────────────────────────────────────────

    private void handlePage(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            respond(ex, 404, "text/plain", "Not Found");
            return;
        }
        String html = loadHtml();
        respond(ex, 200, "text/html; charset=utf-8", html);
    }

    private void handleChat(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }
        String body;
        try (InputStream is = ex.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Expect JSON: {"message":"...","senderId":"..."}
        String message = extractJsonValue(body, "message");
        String senderId = extractJsonValue(body, "senderId");
        if (senderId == null || senderId.isBlank()) senderId = "web-user";
        if (message == null || message.isBlank()) {
            respond(ex, 400, "application/json", "{\"error\":\"empty message\"}");
            return;
        }

        try {
            String reply = handler.onMessage(senderId, message);
            String json = "{\"reply\":" + escapeJsonString(reply) + "}";
            respond(ex, 200, "application/json", json);
        } catch (Exception e) {
            log.error("Error handling chat message: {}", e.getMessage(), e);
            String errorJson = "{\"error\":" + escapeJsonString("Agent error: " + e.getMessage()) + "}";
            respond(ex, 500, "application/json", errorJson);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void handleShutdown(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }
        String body;
        try (InputStream is = ex.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        String token = extractJsonValue(body, "adminToken");
        if (token == null || !token.equals(adminToken)) {
            respond(ex, 403, "application/json", "{\"error\":\"Invalid admin token\"}");
            return;
        }
        log.info("Shutdown requested via WebChat admin API");
        respond(ex, 200, "application/json", "{\"message\":\"Shutting down...\"}");
        // Run shutdown hook in a separate thread to allow response to be sent
        Thread.startVirtualThread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            shutdownHook.run();
        });
    }

    // ── Helpers (continued) ─────────────────────────────────────────────

    private void respond(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Minimal JSON string value extractor — no library needed. */
    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return null;
        idx = json.indexOf('"', idx + 1);
        if (idx < 0) return null;
        int end = idx + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return json.substring(idx + 1, end);
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private String loadHtml() {
        return CHAT_HTML;
    }

    private static final String CHAT_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>JClaw WebChat</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
         background: #1a1a2e; color: #eee; height: 100vh; display: flex; flex-direction: column; }
  #header { padding: 12px 20px; background: #16213e; border-bottom: 1px solid #0f3460;
             font-size: 18px; font-weight: 600; display: flex; justify-content: space-between; align-items: center; }
  #shutdown-btn { padding: 6px 14px; border-radius: 6px; border: 1px solid #c0392b; background: transparent;
                  color: #e74c3c; font-size: 13px; cursor: pointer; }
  #shutdown-btn:hover { background: #c0392b; color: #fff; }
  #messages { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 12px; }
  .msg { max-width: 75%; padding: 10px 14px; border-radius: 12px; line-height: 1.5;
         white-space: pre-wrap; word-break: break-word; }
  .msg.user { align-self: flex-end; background: #0f3460; }
  .msg.agent { align-self: flex-start; background: #222; border: 1px solid #333; }
  .msg.error { align-self: center; background: #5c1a1a; font-size: 13px; }
  #input-bar { display: flex; padding: 12px; background: #16213e; border-top: 1px solid #0f3460; gap: 8px; }
  #input { flex: 1; padding: 10px 14px; border-radius: 8px; border: 1px solid #333;
           background: #1a1a2e; color: #eee; font-size: 15px; outline: none; }
  #input:focus { border-color: #0f3460; }
  #send { padding: 10px 20px; border-radius: 8px; border: none; background: #0f3460;
          color: #eee; font-size: 15px; cursor: pointer; }
  #send:hover { background: #1a4a8a; }
  #send:disabled { opacity: 0.5; cursor: not-allowed; }
  .typing { align-self: flex-start; color: #888; font-size: 13px; padding: 4px 14px; }
</style>
</head>
<body>
<div id="header"><span>JClaw WebChat</span><button id="shutdown-btn">Shutdown</button></div>
<div id="messages"></div>
<div id="input-bar">
  <input id="input" placeholder="Type a message..." autocomplete="off" />
  <button id="send">Send</button>
</div>
<script>
const msgs = document.getElementById('messages');
const input = document.getElementById('input');
const sendBtn = document.getElementById('send');
const senderId = 'web-' + Math.random().toString(36).slice(2, 8);

function addMsg(text, cls) {
  const d = document.createElement('div');
  d.className = 'msg ' + cls;
  d.textContent = text;
  msgs.appendChild(d);
  msgs.scrollTop = msgs.scrollHeight;
  return d;
}

async function send() {
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  addMsg(text, 'user');
  sendBtn.disabled = true;
  const typing = document.createElement('div');
  typing.className = 'typing';
  typing.textContent = 'Agent is thinking...';
  msgs.appendChild(typing);
  msgs.scrollTop = msgs.scrollHeight;
  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, senderId })
    });
    typing.remove();
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    if (data.error) {
      addMsg('Error: ' + data.error, 'error');
    } else {
      addMsg(data.reply, 'agent');
    }
  } catch (e) {
    typing.remove();
    addMsg('Error: ' + e.message, 'error');
  }
  sendBtn.disabled = false;
  input.focus();
}

sendBtn.addEventListener('click', send);
input.addEventListener('keydown', e => { if (e.key === 'Enter') send(); });
input.focus();

document.getElementById('shutdown-btn').addEventListener('click', async () => {
  const token = prompt('Enter admin token to shutdown:');
  if (!token) return;
  const btn = document.getElementById('shutdown-btn');
  btn.disabled = true;
  btn.textContent = 'Shutting down...';
  try {
    const res = await fetch('/api/shutdown', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ adminToken: token })
    });
    const data = await res.json();
    if (data.error) {
      addMsg('Shutdown failed: ' + data.error, 'error');
      btn.disabled = false;
      btn.textContent = 'Shutdown';
    } else {
      addMsg('Server is shutting down...', 'error');
      input.disabled = true;
      sendBtn.disabled = true;
      btn.style.borderColor = '#555';
      btn.style.color = '#555';
    }
  } catch (e) {
    addMsg('Shutdown request failed: ' + e.message, 'error');
    btn.disabled = false;
    btn.textContent = 'Shutdown';
  }
});
</script>
</body>
</html>
""";
}
