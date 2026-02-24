package ru.netelogy;

import java.io.IOException;
import java.io.InputStream;


public class LimitedInputStream extends InputStream {
    private final InputStream original;
    private final long limit;
    private long bytesRead = 0;

    public LimitedInputStream(InputStream original, long limit) {
        this.original = original;
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        if (bytesRead >= limit) {
            return -1; // Достигнут лимит
        }
        int result = original.read();
        if (result != -1) {
            bytesRead++;
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bytesRead >= limit) {
            return -1;
        }

        long remaining = limit - bytesRead;
        int bytesToRead = (int) Math.min(len, remaining);

        int read = original.read(b, off, bytesToRead);
        if (read > 0) {
            bytesRead += read;
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        // Не закрываем оригинальный поток, так как он будет закрыт позже
    }
}
