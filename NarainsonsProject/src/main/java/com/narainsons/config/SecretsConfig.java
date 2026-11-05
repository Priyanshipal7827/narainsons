package com.narainsons.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class SecretsConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = SecretsConfig.class.getClassLoader().getResourceAsStream("secrets.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private SecretsConfig() {
    }

    public static String get(String key) {
        return PROPS.getProperty(key, "");
    }
}
