package ru.netelogy;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws IOException {
        final var server = new Server();

        // Обработчик для GET /messages с поддержкой query параметров
        server.addHandler("GET", "/messages", (request, responseStream) -> {
            // Получаем query параметры
            String last = request.getQueryParam("last");
            String sort = request.getQueryParam("sort");

            StringBuilder response = new StringBuilder();
            response.append("GET messages\n");

            if (last != null) {
                response.append("Last: ").append(last).append("\n");
            }

            if (sort != null) {
                response.append("Sort: ").append(sort).append("\n");
            }

            // Показываем все параметры
            response.append("All params: ").append(request.getQueryParams()).append("\n");

            String responseBody = response.toString();
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    "Content-Length: " + responseBody.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    responseBody;

            responseStream.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            responseStream.flush();
        });

        // Обработчик для POST /messages
        server.addHandler("POST", "/messages", (request, responseStream) -> {
            StringBuilder response = new StringBuilder("POST messages\n");

            // Получаем параметры из query
            response.append("Query params: ").append(request.getQueryParams()).append("\n");

            // Читаем тело запроса
            if (request.getBody() != null) {
                byte[] buffer = new byte[1024];
                int read;
                StringBuilder body = new StringBuilder();
                while ((read = request.getBody().read(buffer)) != -1) {
                    body.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
                response.append("Body: ").append(body).append("\n");
            }

            String responseBody = response.toString();
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    "Content-Length: " + responseBody.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    responseBody;

            responseStream.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            responseStream.flush();
        });

        // Обработчик для поиска пользователей с фильтрацией
        server.addHandler("GET", "/users", (request, responseStream) -> {
            String name = request.getQueryParam("name");
            String age = request.getQueryParam("age");
            String city = request.getQueryParam("city");

            StringBuilder json = new StringBuilder("{");
            json.append("\"filters\": {");

            if (name != null) {
                json.append("\"name\": \"").append(name).append("\"");
            }
            if (age != null) {
                if (name != null) json.append(", ");
                json.append("\"age\": ").append(age);
            }
            if (city != null) {
                if (name != null || age != null) json.append(", ");
                json.append("\"city\": \"").append(city).append("\"");
            }

            json.append("}}");

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + json.toString().getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    json;

            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
            responseStream.flush();
        });

        System.out.println("Server starting...");
        server.listen(9999);
    }
}