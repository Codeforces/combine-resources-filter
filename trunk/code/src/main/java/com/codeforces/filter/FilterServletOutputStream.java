package com.codeforces.filter;

import javax.servlet.ServletOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Mike Mirzayanov (mirzayanovmr@gmail.com)
 */
public class FilterServletOutputStream extends ServletOutputStream {
    private DataOutputStream stream;

    public FilterServletOutputStream(OutputStream stream) {
        this.stream = new DataOutputStream(stream);
    }

    @Override
    public void write(int i) throws IOException {
        stream.write(i);
    }

    @Override
    public void write(byte bytes[]) throws IOException {
        stream.write(bytes);
    }

    @Override
    public void write(byte bytes[], int off, int length)
            throws IOException {
        stream.write(bytes, off, length);
    }

    @Override
    public void flush() throws IOException {
        stream.flush();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
