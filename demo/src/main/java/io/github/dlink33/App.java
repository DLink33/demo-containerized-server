package io.github.dlink33;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class App {
    private static final String HEALTH_OK_JSON = "{\"status\":\"ok\"}\n";

    public static void main(String[] args) throws Exception{
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8888"));
        
        // Bind to ALL interfaces so Docker port-mapping works 
        // (not just for container loopback: 127.0.0.1)
        InetSocketAddress addr = new InetSocketAddress("0.0.0.0", port);
        
        // backlog=0 tells the system to pick a reasonable default connection backlog
        HttpServer server = HttpServer.create(addr, 0);

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        
        server.createContext("/",           App::handleIndex);
        server.createContext("/health",     App::handleHealth);
        server.createContext("/index.js",   App::handleIndexJs);
        server.createContext("/style.css",  App::handleStyleCss);
        
        System.out.println("Listening on http://0.0.0.0:" + port + " ...");


        server.start();
    }
    
    private static boolean requireHttpMethod(HttpExchange ex, String... allowed) throws IOException {
        String actual = ex.getRequestMethod();
        for (String m : allowed) {
            if (m.equalsIgnoreCase(actual)) return true;
        }
        
        ex.getResponseHeaders().set("Allow", String.join(", ", allowed).toUpperCase());
        sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}\n");
        return false;
    }
    
    
    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        send(ex, status, "application/json; charset=utf-8", body);
    }
    
    private static byte[] readResource(String path) throws IOException {
        try (InputStream instream = App.class.getResourceAsStream(path)) {
            if (instream == null) throw new IOException("Missing resource: " + path);
            return instream.readAllBytes();
        }
    }

    private static void serveResourceBytes(HttpExchange ex, String resourcePath, String contentType) throws IOException {
        byte[] body;
        try {
            body = readResource(resourcePath);
        } catch (IOException missing) {
            sendJson(ex, 404, "{\"error\":\"Not Found\"}\n");
            return;
        }
        send(ex, 200, contentType, body);
    }

    private static void serveResource(HttpExchange ex, String resourcePath, String contentType) throws IOException {
        if (!requireHttpMethod(ex, "GET")) return;
        serveResourceBytes(ex, resourcePath, contentType);
    }


    ////////////////////// END POINT HANDLERS /////////////////////////////
    
    private static void handleIndex(HttpExchange ex) throws IOException {
        if (!requireHttpMethod(ex, "GET", "HEAD")) return;

        String path = ex.getRequestURI().getPath();
        if (!path.equals("/") && !path.equals("/index.html")) {
            sendJson(ex, 404, "{\"error\":\"Not Found\"}\n");
            return;
        }
        serveResourceBytes(ex, "/static/index.html", "text/html; charset=utf-8");
    }

    private static void handleHealth(HttpExchange ex) throws IOException{
        if (!requireHttpMethod(ex, "GET")) return;
        try {
            sendJson(ex, 200, HEALTH_OK_JSON);
        } catch (IOException e) {
            System.err.println("Failed to handle /health: " + e);
            try { ex.close(); } catch (Exception ignored) {}
        }
    }

    private static void handleIndexJs(HttpExchange ex) throws IOException {
        serveResource(ex, "/static/index.js", "application/javascript; charset=utf-8");
    }

    private static void handleStyleCss(HttpExchange ex) throws IOException {
        serveResource(ex, "/static/style.css", "text/css; charset=utf-8");
    }

}