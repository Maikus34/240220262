package ru.netelogy;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final List<String> VALID_PATHS = List.of("/index.html", "/spring.svg",
            "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html",
            "/forms.html", "/classic.html", "/events.html", "/events.js");

    private final int PORT = 9999;
    private static final int THREAD_POOL_SIZE = 64;

    private volatile boolean isRunning;
    private final ExecutorService threadPool;

    // Хранилище хендлеров: сначала ключ по методу HTTP, затем по пути (без query)
    private final Map<String, Map<String, Handler>> handlers;

    public Server() {
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.isRunning = true;
        this.handlers = new ConcurrentHashMap<>();

        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Обработчик для GET запросов статических файлов
        addHandler("GET", "/", (request, out) -> {
            String path = request.getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }
            handleStaticFileRequest(path, out);
        });

        // Добавляем обработчики для всех валидных путей
        for (String path : VALID_PATHS) {
            addHandler("GET", path, (request, out) -> handleStaticFileRequest(path, out));
        }
    }


    public void addHandler(String method, String path, Handler handler) {
        // Нормализуем путь: убираем query, если есть, и лишние слеши
        String normalizedPath = normalizePath(path);

        handlers.computeIfAbsent(method.toUpperCase(), k -> new ConcurrentHashMap<>())
                .put(normalizedPath, handler);

        System.out.println("Registered handler: " + method + " " + normalizedPath);
    }

    private String normalizePath(String path) {
        if (path == null) return "/";

        // Убираем query строку
        int queryStart = path.indexOf('?');
        String cleanPath = queryStart >= 0 ? path.substring(0, queryStart) : path;

        // Убираем лишние слеши и нормализуем
        cleanPath = cleanPath.replaceAll("/+", "/");

        // Убеждаемся, что путь начинается с /
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }

        // Для корневого пути возвращаем "/"
        return cleanPath.isEmpty() ? "/" : cleanPath;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (isRunning) {
                final Socket socket = serverSocket.accept();
                threadPool.submit(() -> {
                    try {
                        handleConnection(socket);
                    } catch (IOException e) {
                        System.err.println("Error handling connection: " + e.getMessage());
                    }
                });
            }
        } finally {
            threadPool.shutdown();
        }
    }

    public void listen(int port) throws IOException {
        // Для совместимости с условием задачи
        start();
    }

    private void handleConnection(Socket socket) throws IOException {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            Request request = parseRequest(in, socket.getInputStream());
            if (request == null) {
                sendBadRequest(out);
                return;
            }

            processRequest(request, out);
        }
    }

    private Request parseRequest(BufferedReader in, InputStream socketInputStream) throws IOException {
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            return null;
        }

        String method = parts[0];
        String rawPath = parts[1]; // Сохраняем полный путь с query
        String version = parts[2];

        // Парсим заголовки
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        int contentLength = 0;

        while (!(headerLine = in.readLine()).isEmpty()) {
            int colonIndex = headerLine.indexOf(":");
            if (colonIndex > 0) {
                String headerName = headerLine.substring(0, colonIndex).trim();
                String headerValue = headerLine.substring(colonIndex + 1).trim();
                headers.put(headerName, headerValue);

                if ("Content-Length".equalsIgnoreCase(headerName)) {
                    contentLength = Integer.parseInt(headerValue);
                }
            }
        }

        // Создаем InputStream для тела запроса
        InputStream bodyStream = null;
        if (contentLength > 0) {
            bodyStream = new LimitedInputStream(socketInputStream, contentLength);
        }

        return new Request(method, rawPath, version, headers, bodyStream);
    }

    private void processRequest(Request request, BufferedOutputStream out) throws IOException {
        String method = request.getMethod();
        String path = request.getPath(); // Используем путь без query для поиска хендлера

        // Ищем подходящий хендлер
        Handler handler = findHandler(method, path);

        if (handler != null) {
            try {
                // Вызываем зарегистрированный хендлер
                handler.handle(request, out);
            } catch (Exception e) {
                System.err.println("Error in handler for " + method + " " + path + ": " + e.getMessage());
                sendInternalServerError(out);
            }
        } else {
            // Если хендлер не найден, возвращаем 404
            sendNotFound(out);
        }
    }

    private Handler findHandler(String method, String path) {
        Map<String, Handler> methodHandlers = handlers.get(method.toUpperCase());
        if (methodHandlers != null) {
            // Нормализуем путь для поиска
            String normalizedPath = normalizePath(path);

            // Сначала ищем точное совпадение пути
            Handler exactMatch = methodHandlers.get(normalizedPath);
            if (exactMatch != null) {
                return exactMatch;
            }

            // Затем ищем обработчик для корневого пути
            if ("/".equals(normalizedPath)) {
                return methodHandlers.get("/");
            }

            // Проверяем, может быть это статический файл
            if (VALID_PATHS.contains(normalizedPath)) {
                return methodHandlers.get(normalizedPath);
            }
        }
        return null;
    }

    private void handleStaticFileRequest(String path, BufferedOutputStream out) throws IOException {
        if (!isValidPath(path)) {
            sendNotFound(out);
            return;
        }

        Path filePath = Path.of(".", "public", path);

        if (!Files.exists(filePath)) {
            sendNotFound(out);
            return;
        }

        String mimeType = Files.probeContentType(filePath);

        if (isClassicHtml(path)) {
            sendClassicHtmlResponse(out, filePath, mimeType);
        } else {
            sendFileResponse(out, filePath, mimeType);
        }
    }

    private void sendFileResponse(BufferedOutputStream out, Path filePath, String mimeType) throws IOException {
        long fileSize = Files.size(filePath);
        sendResponseHeaders(out, 200, mimeType, fileSize);
        Files.copy(filePath, out);
        out.flush();
    }

    private void sendResponseHeaders(BufferedOutputStream out, int statusCode,
                                     String mimeType, long contentLength) throws IOException {
        String statusText = statusCode == 200 ? "OK" : "Not Found";
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
    }

    private void sendClassicHtmlResponse(BufferedOutputStream out, Path filePath, String mimeType) throws IOException {
        String template = Files.readString(filePath);
        byte[] content = template.replace(
                "{time}",
                LocalDateTime.now().toString()
        ).getBytes();

        sendResponseHeaders(out, 200, mimeType, content.length);
        out.write(content);
        out.flush();
    }

    private boolean isClassicHtml(String path) {
        return "/classic.html".equals(path);
    }

    private void sendNotFound(BufferedOutputStream out) throws IOException {
        String response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.flush();
    }

    private void sendBadRequest(BufferedOutputStream out) throws IOException {
        String response = "HTTP/1.1 400 Bad Request\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.flush();
    }

    private boolean isValidPath(String path) {
        return VALID_PATHS.contains(path);
    }

    private void sendInternalServerError(BufferedOutputStream out) throws IOException {
        String response = "HTTP/1.1 500 Internal Server Error\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.flush();
    }

    public void stop() {
        isRunning = false;
    }
}