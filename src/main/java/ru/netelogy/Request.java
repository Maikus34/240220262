package ru.netelogy;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Request {
    private final String method;
    private final String path;           // это поле класса
    private final String rawPath;
    private final String version;
    private final Map<String, String> headers;
    private final InputStream body;

    private Map<String, String> queryParams;

    public Request(String method, String rawPath, String version,
                   Map<String, String> headers, InputStream body) {
        this.method = method;
        this.rawPath = rawPath;
        this.version = version;
        this.headers = headers != null ? headers : new HashMap<>();
        this.body = body;

        // Разбираем путь и query параметры
        ParsedPath parsedPath = parsePathAndQuery(rawPath);
        this.path = parsedPath.path;           // присваиваем полю класса
        this.queryParams = parsedPath.queryParams;
    }

    private ParsedPath parsePathAndQuery(String rawPath) {
        int queryStart = rawPath.indexOf('?');
        if (queryStart >= 0) {
            String cleanPath = rawPath.substring(0, queryStart);
            String queryString = rawPath.substring(queryStart + 1);
            return new ParsedPath(cleanPath, parseQueryString(queryString));
        } else {
            return new ParsedPath(rawPath, new HashMap<>());
        }
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();

        if (queryString == null || queryString.isEmpty()) {
            return params;
        }

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            try {
                String key;
                String value;

                if (eqIndex > 0) {
                    key = URLDecoder.decode(pair.substring(0, eqIndex), StandardCharsets.UTF_8.name());
                    value = URLDecoder.decode(pair.substring(eqIndex + 1), StandardCharsets.UTF_8.name());
                } else {
                    key = URLDecoder.decode(pair, StandardCharsets.UTF_8.name());
                    value = "";
                }

                params.put(key, value);

            } catch (UnsupportedEncodingException e) {
                // Игнорируем проблемный параметр
            }
        }

        return params;
    }

    // Вспомогательный класс для возврата двух значений
    private static class ParsedPath {
        final String path;
        final Map<String, String> queryParams;

        ParsedPath(String path, Map<String, String> queryParams) {
            this.path = path;
            this.queryParams = queryParams;
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getRawPath() {
        return rawPath;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public InputStream getBody() {
        return body;
    }

    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    public Map<String, String> getQueryParams() {
        return new HashMap<>(queryParams);
    }

    public boolean hasQueryParam(String name) {
        return queryParams.containsKey(name);
    }
}