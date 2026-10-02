package io.github.yuraburyakov.casttomarkdown;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lets at most {@code limit} bytes through and throws {@link DocumentTooLargeException} on the next one,
 * so a too large stream is stopped while it is read, not after it is in memory.
 * Never closed by the library: closing it would close the caller's stream.
 */
final class LimitedInputStream extends FilterInputStream {

    private final long limit;
    private final String name;
    private long count;

    LimitedInputStream(InputStream in, long limit, String name) {
        super(in);
        this.limit = limit;
        this.name = name;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            count(1);
        }
        return b;
    }

    /** Asks for at most one byte over the limit, so the caller's stream is not read further than that. */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        long remaining = limit - count; // never negative: count > limit has already thrown
        int n = super.read(buffer, offset, remaining >= length ? length : (int) remaining + 1);
        if (n > 0) {
            count(n);
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        count(skipped);
        return skipped;
    }

    /** No mark/reset: bytes read again after a reset would be counted twice. */
    @Override
    public boolean markSupported() {
        return false;
    }

    private void count(long bytes) {
        count += bytes;
        if (count > limit) {
            throw new DocumentTooLargeException("Document is larger than the limit of " + limit + " bytes: " + name);
        }
    }
}
