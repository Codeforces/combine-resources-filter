package com.codeforces.filter;

import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;

/**
 * If finds head-section in the response and changes it to
 * contain combined links (css) and scripts (js).
 *
 * @author Mike Mirzayanov (mirzayanovmr@gmail.com)
 */
public class CombineResourcesFilter extends PostprocessFilter {
    @Override
    public String postprocess(HttpServletRequest request, String responseText) throws IOException {
        if (responseText == null) {
            return responseText;
        }

        int headStart = StringUtils.indexOfIgnoreCase(responseText, "<head>");
        if (headStart >= 0) {
            int headFinish = StringUtils.indexOfIgnoreCase(responseText, "</head>", headStart);
            if (headFinish >= 0) {
                String head = responseText.substring(headStart, headFinish + "</head>".length());
                try {
                    head = CombineResourcesUtil.preprocessHead(head, new URL(request.getRequestURL().toString()));
                    return responseText.substring(0, headStart) + head + responseText.substring(headFinish + "</head>".length());
                } catch (ParseException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }

        return responseText;
    }
}
