package com.codeforces.filter;

import com.codeforces.jrun.Outcome;
import com.codeforces.jrun.Params;
import com.codeforces.jrun.ProcessRunner;
import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.html.dom.HTMLDocumentImpl;
import org.apache.log4j.Logger;
import org.cyberneko.html.parsers.DOMFragmentParser;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.w3c.dom.*;
import org.w3c.dom.html.HTMLDocument;
import org.xml.sax.InputSource;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Mike Mirzayanov (mirzayanovmr@gmail.com)
 */
public class CombineResourcesUtil {
    /**
     * Cache just from the hash of the head section to the postprocessed head section.
     */
    private static final Map<String, String> cache = new ConcurrentHashMap<String, String>();

    /**
     * Logger.
     */
    private static final Logger logger = Logger.getLogger(CombineResourcesUtil.class);

    private static final Random RANDOM = new Random();

    private static final Lock lock = new ReentrantLock();

    private static String internalBuildUrl(URL base, String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        if (path.startsWith("http:")) {
            return path;
        }

        char c = path.charAt(0);

        if (c == '/') {
            return base.getProtocol() + "://"
                    + base.getHost()
                    + (base.getPort() != 80 && base.getPort() != -1 ? ":" + base.getPort() : "")
                    + path;
        } else {
            return base + "/../" + path;
        }
    }

    /**
     * @param base Base document URL.
     * @param path Resource path (relative or absolute).
     * @return Absolute path to the resource.
     */
    private static String buildUrl(URL base, String path) {
        String url = internalBuildUrl(base, path);
        if (!url.contains("?")) {
            return url + "?" + Long.toHexString(RANDOM.nextLong());
        } else {
            return url;
        }
    }

    /**
     * @param url Resource URL.
     * @return Resource filename, used last part of the URL without "?query" section.
     */
    private static String fileName(URL url) {
        String[] tokens = url.toString().split("/");
        String last = tokens[tokens.length - 1];
        if (last.indexOf('?') < 0) {
            return last;
        } else {
            return last.substring(0, last.indexOf('?'));
        }
    }

    /**
     * @param base     Document base URL.
     * @param linkCss  Consecutive <link>-nodes from the head which differs only in "href" attrubute.
     * @param newFiles Created combined files (actually at most one).
     * @return Postprocessed node, actually it is the first node from linkCss, but with change in "href".
     * @throws IOException if can IO.
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public static Node combineCss(URL base, List<Node> linkCss, List<File> newFiles) throws IOException {
        File dir = File.createTempFile("css", "" + System.currentTimeMillis());
        StringBuilder name = new StringBuilder();

        try {
            if (dir.delete() && dir.mkdirs()) {
                File minDir = new File(dir, "min");
                minDir.mkdir();

                File combineFile = new File(minDir, "style.css");
                Writer writer = new FileWriter(combineFile);

                boolean first = true;
                for (Node link : linkCss) {
                    String path = ((Element) link).getAttribute("href");

                    URL url = new URL(buildUrl(base, path));
                    InputStream inputStream = url.openStream();

                    File cssFile = new File(dir, fileName(url));

                    FileOutputStream outputStream = new FileOutputStream(cssFile);
                    IOUtils.copy(inputStream, outputStream);
                    outputStream.close();
                    inputStream.close();
                    //logger.debug("Downloaded " + cssFile + "[type=js].");

                    if (!first) {
                        writer.write('\n');
                    } else {
                        first = false;
                    }

                    Reader reader = new FileReader(cssFile);
                    if (Configuration.cssMinification()) {
                        CssCompressor cssCompressor = new CssCompressor(reader);
                        cssCompressor.compress(writer, 0);
                    } else {
                        IOUtils.copy(reader, writer);
                    }
                    reader.close();

                    String fileName = cssFile.getName();
                    int pos = fileName.lastIndexOf('.');
                    if (pos >= 0) {
                        fileName = fileName.substring(0, pos);
                    }
                    name.append(fileName).append(",");
                }

                writer.close();
                FileReader reader = new FileReader(combineFile);
                String newName = hashCode(name.toString()) + "_" + hashCode(IOUtils.toString(reader)) + ".css";
                name = new StringBuilder(newName);
                reader.close();

                File targetFile = new File(Configuration.getCssLocalDir(), name.toString());
                if (!targetFile.exists()) {
                    targetFile.getParentFile().mkdirs();
                    FileUtils.copyFile(combineFile, targetFile);
                    newFiles.add(targetFile);
                    logger.info("Combined several css files into the single " + targetFile + " [size=" + targetFile.length() + "].");
                }
            }
        } finally {
            FileUtils.deleteQuietly(dir);
        }

        if (name.length() != 0) {
            Element element = (Element) linkCss.get(0);
            element.setAttribute("href", Configuration.getCssUrlPrefix() + name.toString());
            return element;
        } else {
            return null;
        }
    }

    /**
     * @param base     Document base URL.
     * @param linkJs   Consecutive <script>-nodes from the head which differs only in "src" attrubute.
     * @param newFiles Created combined files (actually at most one).
     * @return Postprocessed node, actually it is the first node from linkJs, but with change in "src".
     * @throws IOException if can IO.
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public static Node combineJs(URL base, List<Node> linkJs, List<File> newFiles) throws IOException {
        File dir = File.createTempFile("javascript", "" + System.currentTimeMillis());
        StringBuilder name = new StringBuilder();

        try {
            if (dir.delete() && dir.mkdirs()) {
                File minDir = new File(dir, "min");
                minDir.mkdir();

                File combineFile = new File(minDir, "script.js");
                File concatFile = new File(minDir, "concat.js");
                Writer combineWriter = new FileWriter(combineFile);
                Writer concatWriter = new FileWriter(concatFile);
                final List<Boolean> fails = new LinkedList<Boolean>();

                boolean first = true;
                for (Node link : linkJs) {
                    String path = ((Element) link).getAttribute("src");

                    URL url = new URL(buildUrl(base, path));
                    InputStream inputStream = url.openStream();

                    File jsFile = new File(dir, fileName(url));

                    FileOutputStream outputStream = new FileOutputStream(jsFile);
                    IOUtils.copy(inputStream, outputStream);
                    outputStream.close();
                    inputStream.close();
                    //logger.debug("Downloaded " + jsFile + "[type=js].");

                    if (!first) {
                        combineWriter.write("\n;\n");
                        concatWriter.write("\n;\n");
                    } else {
                        first = false;
                    }

                    if (Configuration.jsMinification()) {
                        Reader reader = new FileReader(jsFile);
                        try {
                            JavaScriptCompressor jsCompressor = new JavaScriptCompressor(reader, new ErrorReporter() {
                                @Override
                                public void warning(String s, String s1, int i, String s2, int i1) {
                                    fails.add(true);
                                }

                                @Override
                                public void error(String s, String s1, int i, String s2, int i1) {
                                    fails.add(true);
                                }

                                @Override
                                public EvaluatorException runtimeError(String s, String s1, int i, String s2, int i1) {
                                    fails.add(true);
                                    return null;
                                }
                            });
                            jsCompressor.compress(combineWriter, 0, false, false, true, true);
                        } catch (Exception e) {
                            fails.add(true);
                        }
                        reader.close();
                    }

                    Reader reader = new FileReader(jsFile);
                    IOUtils.copy(reader, concatWriter);
                    reader.close();

                    String fileName = jsFile.getName();
                    int pos = fileName.lastIndexOf('.');
                    if (pos >= 0) {
                        fileName = fileName.substring(0, pos);
                    }
                    name.append(fileName).append(",");
                }

                combineWriter.close();
                concatWriter.close();

                FileReader reader;
                if (fails.size() == 0 && Configuration.jsMinification()) {
                    reader = new FileReader(combineFile);
                } else {
                    reader = new FileReader(concatFile);
                }
                String newName = hashCode(name.toString()) + "_" + hashCode(IOUtils.toString(reader)) + ".js";
                name = new StringBuilder(newName);
                reader.close();

                File targetFile = new File(Configuration.getJsLocalDir(), name.toString());
                if (!targetFile.exists()) {
                    targetFile.getParentFile().mkdirs();
                    if (fails.size() == 0 && Configuration.jsMinification()) {
                        FileUtils.copyFile(combineFile, targetFile);
                    } else {
                        FileUtils.copyFile(concatFile, targetFile);
                    }
                    newFiles.add(targetFile);
                    logger.info("Combined several js files into the single " + targetFile + " [size=" + targetFile.length() + "].");
                }
            }
        } finally {
            FileUtils.deleteQuietly(dir);
        }

        if (name.length() != 0) {
            Element element = (Element) linkJs.get(0);
            element.setAttribute("src", Configuration.getJsUrlPrefix() + name.toString());
            return element;
        } else {
            return null;
        }
    }

    /**
     * Parses fragment of HTML.
     *
     * @param s    String to parse.
     * @param from Beginning index of fragment in the string s.
     * @param to   Ending index of fragment in the string s.
     * @return Parsed fragment as DocumentFragment.
     * @throws ParseException Can't parse.
     */
    private static DocumentFragment parseFragment(String s, int from, int to) throws ParseException {
        try {
            DOMFragmentParser parser = new DOMFragmentParser();
            HTMLDocument document = new HTMLDocumentImpl();
            DocumentFragment fragment = document.createDocumentFragment();
            parser.parse(new InputSource(new StringReader(s.substring(from, to))), fragment);
            return fragment;
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    /**
     * Divides all nodes to three types.
     * <p/>
     * The first type (1) are link-elements with
     * rel=stylesheet,type=text/css,charset=utf-8.
     * <p/>
     * The second type (2) are script-elements with
     * type=text/javascript and non-empty src attribute.
     * <p/>
     * Other nodes have type=-1.
     *
     * @param node Node to analyze.
     * @return Type (1, 2 or -1).
     */
    private static int getType(Node node) {
        if (node instanceof Element) {
            Element element = (Element) node;

            String id = element.getAttribute("id");
            if (id.startsWith("nocomb")) {
                return -1;
            }

            if (element.getTagName().equalsIgnoreCase("link")
                    && element.getAttribute("rel").equalsIgnoreCase("stylesheet")
                    && element.getAttribute("type").equalsIgnoreCase("text/css")
                    && element.getAttribute("charset").equalsIgnoreCase("utf-8")) {
                return 1;
            }

            if (element.getTagName().equalsIgnoreCase("script")
                    && element.getAttribute("type").equalsIgnoreCase("text/javascript")
                    && element.getAttribute("src") != null && element.getAttribute("src").length() > 0) {
                return 2;
            }
        }

        return -1;
    }

    /**
     * Given DocumentFragment, it returns it as List<List<Node>>. It merges all the consecutive
     * nodes with equal types (type=1 or type=2) in a single list List<Node>, which will be lately
     * combined.
     *
     * @param fragment DocumentFragment
     * @return Fragment as List<List<Node>>, where element-sequences which should be combined are
     *         merged in the single List<Node>.
     */
    private static List<List<Node>> split(Node fragment) {
        List<List<Node>> elements = new ArrayList<List<Node>>();

        int previousType = -1;
        List<Node> current = new LinkedList<Node>();

        NodeList nodeList = fragment.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Text) {
                String text = node.getTextContent();
                if (StringUtils.isBlank(text)) {
                    continue;
                }

            }
            int type = getType(node);

            if (type > 0 && type == previousType) {
                current.add(node);
            } else {
                if (current.size() > 0) {
                    elements.add(current);
                }
                current = new LinkedList<Node>();
                current.add(node);
                previousType = type;
            }
        }

        if (current.size() > 0) {
            elements.add(current);
        }

        return elements;
    }

    /**
     * @param fragment DocumentFragment.
     * @return DocumentFragment as valid XHTML.
     * @throws ParseException if can't parse.
     */
    private static String toString(Node fragment) throws ParseException {
        try {
            DOMSource domSource = new DOMSource(fragment);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "yes");
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    /**
     * @param s String to calculate hash.
     * @return Strong hash (used 128 bit), which used to uniquely determine a string.
     */
    private static String hashCode(String s) {
        return hashCode(s, 0, s.length());
    }

    /**
     * Calculates hash for substring of s.
     *
     * @param s    String to calculate hash.
     * @param from Beginning index.
     * @param to   Ending index.
     * @return Strong hash (used 128 bit), which used to uniquely determine a string.
     */
    private static String hashCode(String s, int from, int to) {
        long h0 = 0;
        long h1 = 1;

        for (int i = from; i < to; i++) {
            h0 = h0 * 1009L + (int) s.charAt(i);
            h1 = h1 * 659L + (int) s.charAt(i) * 31L;
        }

        return Long.toHexString(h0) + Long.toHexString(h1);
    }

    /**
     * Runs specified command in the specified directory.
     *
     * @param dir       Home directory for running command.
     * @param command   Command to be executed.
     * @param timelimit Time limit in milliseconds.
     * @throws IOException if can't IO.
     */
    private static void runCommand(String dir, final String command, long timelimit) throws IOException {
        Params params = new Params.Builder().setTimeLimit(timelimit).setDirectory(new File(dir)).newInstance();
        Outcome outcome = ProcessRunner.run(command, params);
        if (outcome.getExitCode() != 0) {
            logger.warn("Process \"" + command + "\" completed, exit code: " + outcome.getExitCode() + ".");
        } else {
            logger.info("Process \"" + command + "\" completed.");
        }
    }

    /**
     * @param head        HTML head-section: looks like "<head>....</head>".
     * @param documentUrl Document URL, which contains the given head.
     * @return Postprocessed head which contains combined CSS and JS.
     * @throws ParseException on parse errors.
     * @throws IOException    on IO errors.
     */
    public static String preprocessHead(String head, URL documentUrl) throws ParseException, IOException {
        head = ensuresHead(head);

        String hashCode = hashCode(head);
        String result = cache.get(hashCode);

        if (result != null) {
            return result;
        } else {
            lock.lock();
            try {
                result = cache.get(hashCode);
                if (result != null) {
                    return result;
                } else {
                    result = doPreprocessHead(head, documentUrl, hashCode);

                    result = result.replace("<HEAD>", "");
                    result = result.replace("</HEAD>", "");

                    return result;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private static String ensuresHead(String head) {
        if (StringUtils.indexOfIgnoreCase(head, "<head>") < 0) {
            head = "<head>" + head + "</head>";
        }
        return head;
    }

    private static String doPreprocessHead(String head, URL documentUrl, String hashCode) throws ParseException, IOException {
        String result;

        Node fragment = parseFragment(head, 0, head.length());
        while (!fragment.getNodeName().equalsIgnoreCase("HEAD")) {
            fragment = fragment.getFirstChild();
        }

        List<List<Node>> elements = split(fragment);

        for (List<Node> nodeList : elements) {
            for (Node node : nodeList) {
                if (node instanceof Element && ((Element) node).getTagName().equalsIgnoreCase("SCRIPT") && StringUtils.isEmpty(node.getTextContent())) {
                    node.setTextContent(" ");
                }
            }
        }

        boolean cssUpdated = false;
        boolean jsUpdated = false;

        for (List<Node> element : elements) {
            if (element.size() > 1 && getType(element.get(0)) == 1) {
                List<File> newFiles = new ArrayList<File>();
                Node combineNode = combineCss(documentUrl, element, newFiles);
                if (!newFiles.isEmpty()) {
                    cssUpdated = true;
                }
                if (combineNode != null) {
                    for (Node node : element) {
                        if (node != combineNode) {
                            node.getParentNode().removeChild(node);
                        }
                    }
                }
            }

            if (element.size() > 1 && getType(element.get(0)) == 2) {
                List<File> newFiles = new ArrayList<File>();
                Node combineNode = combineJs(documentUrl, element, newFiles);
                if (!newFiles.isEmpty()) {
                    jsUpdated = true;
                }
                if (combineNode != null) {
                    for (Node node : element) {
                        if (node != combineNode) {
                            node.getParentNode().removeChild(node);
                        }
                    }
                }
            }
        }

        if (cssUpdated && !StringUtils.isBlank(Configuration.getCssCommandAfterUpdate())) {
            runCommand(Configuration.getCssLocalDir(), Configuration.getCssCommandAfterUpdate(), Configuration.getCommandAfterUpdateTimelimit());
        }

        if (jsUpdated && !StringUtils.isBlank(Configuration.getJsCommandAfterUpdate())) {
            runCommand(Configuration.getJsLocalDir(), Configuration.getJsCommandAfterUpdate(), Configuration.getCommandAfterUpdateTimelimit());
        }

        result = toString(fragment);
        cache.put(hashCode, result);

        return result;
    }
}
