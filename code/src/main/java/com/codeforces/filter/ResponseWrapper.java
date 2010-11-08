package com.codeforces.filter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * @author Mike Mirzayanov (mirzayanovmr@gmail.com)
 */
public class ResponseWrapper extends HttpServletResponseWrapper {
    private ByteArrayOutputStream outputStream;
    private PrintWriter writer;
    private HttpServletResponse response;
    private String contentType;

    public ResponseWrapper(HttpServletResponse response) {
        super(response);
        this.response = response;
        outputStream = new ByteArrayOutputStream();
    }

    public byte[] getBytes() {
        close();
        return outputStream.toByteArray();
    }

    public ServletOutputStream getOutputStream() {
        return new FilterServletOutputStream(outputStream);
    }

    public PrintWriter getWriter()
            throws IOException {
        if (writer != null) {
            return writer;
        } else {
            ServletOutputStream stream = getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(stream, response.getCharacterEncoding()));
            return writer;
        }
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
        super.setContentType(contentType);
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }

        if (outputStream != null) {
            outputStream.flush();
        }
    }

    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            // No operations.
        }
    }
}
