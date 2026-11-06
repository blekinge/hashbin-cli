package net.hashbin.cli.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * An {@link InputStream} that feeds every byte it reads into one or more {@link MessageDigest}s.
 * Reading the stream to its end computes the digests as a side effect, so the results can be read
 * from the digests afterwards. Passing several digests hashes the same bytes with multiple
 * algorithms in a single pass.
 */
public class ChecksumInputStream extends FilterInputStream {

    private final MessageDigest[] digests;

    public ChecksumInputStream(final InputStream stream, final MessageDigest... digests) {
        super(stream);
        this.digests = digests;
    }

    @Override
    public int read() throws IOException {
        final int b = in.read();
        if (b != -1) {
            for (MessageDigest digest : digests) {
                digest.update((byte) b);
            }
        }
        return b;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        final int read = in.read(b);
        if (read > 0) {
            updateAll(b, 0, read);
        }
        return read;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int read = in.read(b, off, len);
        if (read > 0) {
            updateAll(b, off, read);
        }
        return read;
    }

    @Override
    public void reset() throws IOException {
        in.reset();
        for (MessageDigest digest : digests) {
            digest.reset();
        }
    }

    private void updateAll(final byte[] b, final int off, final int len) {
        for (MessageDigest digest : digests) {
            digest.update(b, off, len);
        }
    }
}
