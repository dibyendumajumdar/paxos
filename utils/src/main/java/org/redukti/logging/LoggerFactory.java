/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.logging;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple wrapper around Log4J/JDK logging facilities.
 */
public class LoggerFactory {

    /**
     * Configures the logging system
     */
    public LoggerFactory() {
        String logFile = System.getProperty("logging.properties.file",
                "classpath:paxos.logging.properties");
        configureJDKLogging(logFile);
    }

    void configureJDKLogging(String name) {
        final String classpathPrefix = "classpath:";
        boolean searchClasspath = false;
        String filename = name;
        if (filename.startsWith(classpathPrefix)) {
            filename = filename.substring(classpathPrefix.length());
            searchClasspath = true;
        }
        InputStream is = null;
        try {
            if (searchClasspath) {
                is = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(filename);
            } else {
                is = new FileInputStream(filename);
            }
            java.util.logging.LogManager.getLogManager().readConfiguration(is);
        } catch (Exception e) {
            System.err
                    .println("Failed to initialize JDK 1.4 logging system due to following error: "
                            + e.getMessage());
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public Logger getLogger(String name) {
        return new Logger(name);
    }

    public static final LoggerFactory DEFAULT = new LoggerFactory();
}
