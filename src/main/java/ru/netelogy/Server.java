package ru.netelogy;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final List<String> VALID_PATHS = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private final int PORT = 9999;
    private static final int THREAD_POOL_SIZE = 64;


    private volatile boolean isRunning;

    private final ExecutorService threadPool;

    /*
    Создается пул из 64 потоков: Executors.newFixedThreadPool(64)
    isRunning устанавливается в true
    Сервер готов к запуску через server.start()
     */

    public Server() {
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.isRunning = true;
    }


    /*
    serverSocket.accept() - сервер БЛОКИРУЕТСЯ и ждет подключения клиента
    Клиент подключился - метод возвращает Socket для общения с этим клиентом
    threadPool.submit() - отправляем задачу в пул потоков
    Пул потоков - берет свободный поток из 64 и выполняет handleConnection(socket)
    Цикл повторяется - сервер снова ждет следующего клиента
     */
    public void start() throws IOException {
        try(ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port"  + PORT);

            while (isRunning) {
                //пока подключения нет, код дальше не идет
                final Socket socket = serverSocket.accept();
                //мы говорим свободному потоку, чтобы он поработал с клиентом.
                threadPool.submit(() -> {
                    try {
                        handleConnection(socket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

        }
    }

    private void handleConnection(Socket socket) throws IOException {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
                 //читаем, что хочет клиент, например страницу index.html
                 Request request = parseRequest(in);
                 //проверяем сказал ли что - то клиент
                 if (request == null) {
                    return; // ничего не сказал
                 }
                 //Отвечаем на то, что он попросил
                 processRequest(request, out);
        }
    }

    /*
    Ответ клиенту на вопрос
     */
    private void processRequest(Request request, BufferedOutputStream out) throws IOException {
        String path = request.getPath(); // узнаем, что от нас хочет клиент
        if (!isValidPath(path)) {  // Есть ли этот путь в VALID_PATHS?
            sendNotFound(out);     // Нет такого ресурса! 404
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
        // сеть не понимает строки, она понимает только байты, поэтому мы преобразовываем строку в байты.
        out.write(response.getBytes());
        out.flush();
    }

    /*
    Читаем, что хочет клиент
     */
    private Request parseRequest(BufferedReader in) throws IOException {
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            return null;
        }

        return new Request(parts[0], parts[1], parts[2]);
    }


    /*
    Проверяем есть ли ресурс, который запрашивает пользователь в VALID_PATHS
     */
    private boolean isValidPath(String path) {
        return VALID_PATHS.contains(path);
    }


}
