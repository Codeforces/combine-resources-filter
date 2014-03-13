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
    private static final String CUSTOM_COMBINE_OPEN = "<!--CombineResourcesFilter-->";
    private static final String CUSTOM_COMBINE_CLOSE = "<!--/CombineResourcesFilter-->";

    @Override
    public String postprocess(HttpServletRequest request, String responseText) throws IOException {
        if (responseText == null) {
            return responseText;
        }

        String openTag = "<head>";
        String closeTag = "</head>";

        if (responseText.contains(CUSTOM_COMBINE_OPEN)) {
            openTag = CUSTOM_COMBINE_OPEN;
            closeTag = CUSTOM_COMBINE_CLOSE;
        }

        int headStart = StringUtils.indexOfIgnoreCase(responseText, openTag);
        if (headStart >= 0) {
            int headFinish = StringUtils.indexOfIgnoreCase(responseText, closeTag, headStart);
            if (headFinish >= 0) {
                String head = responseText.substring(headStart, headFinish + closeTag.length());
                try {
                    head = CombineResourcesUtil.preprocessHead(head, new URL(request.getRequestURL().toString()));
                    return responseText.substring(0, headStart + openTag.length())
                            + head
                            + responseText.substring(headFinish);
                } catch (ParseException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }

        return responseText;
    }
}
