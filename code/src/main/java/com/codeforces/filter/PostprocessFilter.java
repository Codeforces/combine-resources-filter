package com.codeforces.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Mike Mirzayanov (mirzayanovmr@gmail.com)
 */
public abstract class PostprocessFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No operations.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse
                && Configuration.isFilterEnabled()) {
            ResponseWrapper responseWrapper = new ResponseWrapper((HttpServletResponse) response);
            chain.doFilter(request, responseWrapper);
            String contentType = responseWrapper.getContentType();

            OutputStream outputStream = response.getOutputStream();
            byte[] bytes = responseWrapper.getBytes();

            if (Configuration.getProcessTypes().contains(contentType)) {
                String encoding = response.getCharacterEncoding();
                String responseText = new String(bytes, encoding);
                String postprocessedText = postprocess((HttpServletRequest) request, responseText);
                bytes = postprocessedText.getBytes(encoding);
            }

            response.setContentLength(bytes.length);
            outputStream.write(bytes);
            outputStream.flush();
        } else {
            chain.doFilter(request, response);
        }
    }

    public abstract String postprocess(HttpServletRequest request, String responseText) throws IOException;

    @Override
    public void destroy() {
        // No operations.
    }
}
