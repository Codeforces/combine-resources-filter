package com.codeforces.filter;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Mike Mirzayanov (mirzayanovmr@gmail.com)
 */
public class Configuration {
    private static final Properties properties = new Properties();

    public static String getCssUrlPrefix() {
        return properties.getProperty("css-url-prefix");
    }

    public static String getCssLocalDir() {
        return properties.getProperty("css-local-dir");
    }

    public static String getCssCommandAfterUpdate() {
        return properties.getProperty("css-command-after-update");
    }

    public static boolean cssMinification() {
        return Boolean.valueOf(properties.getProperty("css-minification"));
    }

    public static String getJsUrlPrefix() {
        return properties.getProperty("js-url-prefix");
    }

    public static String getJsLocalDir() {
        return properties.getProperty("js-local-dir");
    }

    public static String getJsCommandAfterUpdate() {
        return properties.getProperty("js-command-after-update");
    }

    public static boolean jsMinification() {
        return Boolean.valueOf(properties.getProperty("js-minification"));
    }

    public static long getCommandAfterUpdateTimelimit() {
        return Long.parseLong(properties.getProperty("command-after-update-timelimit"));
    }

    public static boolean isFilterEnabled() {
        return Boolean.parseBoolean(properties.getProperty("filter-enabled", "true"));
    }

    static {
        try {
            properties.load(Configuration.class.getResourceAsStream("/CombineResourcesFilter.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
