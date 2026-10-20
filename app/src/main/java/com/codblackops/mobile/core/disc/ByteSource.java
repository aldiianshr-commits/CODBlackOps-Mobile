package com.codblackops.mobile.core.disc;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Random-access reader used by {@link DiscHeaderParser}. Implementations exist for plain files
 * (RandomAccessFile) and for Storage Access Framework documents (FileChannel of a
 * ParcelFileDescriptor, see storage/SafByteSource.kt).
 */
public interface ByteSource extends Closeable {
    /** Total size in bytes, or -1 if unknown. */
    long size() throws IOException;

    /**
     * Reads up to {@code len} bytes starting at absolute {@code offset}.
     *
     * @return number of bytes read, -1 at end of data.
     */
    int read(long offset, byte[] buffer, int bufferOffset, int len) throws IOException;

    /** Reads exactly {@code len} bytes or throws. */
    default void readFully(long offset, byte[] buffer, int bufferOffset, int len) throws IOException {
        int done = 0;
        while (done < len) {
            int n = read(offset + done, buffer, bufferOffset + done, len - done);
            if (n <= 0) {
                throw new IOException("Unexpected end of data at offset " + (offset + done));
            }
            done += n;
        }
    }

    /** ByteSource over a local file. */
    final class FileSource implements ByteSource {
        private final RandomAccessFile file;

        public FileSource(java.io.File f) throws IOException {
            this.file = new RandomAccessFile(f, "r");
        }

        @Override
        public long size() throws IOException {
            return file.length();
        }

        @Override
        public int read(long offset, byte[] buffer, int bufferOffset, int len) throws IOException {
            file.seek(offset);
            return file.read(buffer, bufferOffset, len);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

    /** ByteSource over an in-memory array (tests, small headers). */
    final class ArraySource implements ByteSource {
        private final byte[] data;

        public ArraySource(byte[] data) {
            this.data = data;
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public int read(long offset, byte[] buffer, int bufferOffset, int len) {
            if (offset >= data.length) return -1;
            int n = (int) Math.min(len, data.length - offset);
            System.arraycopy(data, (int) offset, buffer, bufferOffset, n);
            return n;
        }

        @Override
        public void close() {
        }
    }
}
