/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.logging;

/**
 * A simple wrapper around Log4J/JDK logging facilities.
 */
public class Logger {

    private java.util.logging.Logger realLogger;

    public Logger(String name) {
        realLogger = java.util.logging.Logger.getLogger(name);
    }

    public void info(Class<?> sourceClass, String sourceMethod, String message) {
        realLogger.logp(java.util.logging.Level.INFO, sourceClass.getName(),
                sourceMethod, message);
    }

    public void info(Class<?> sourceClass, String sourceMethod, String message,
                     Throwable thrown) {
        realLogger.logp(java.util.logging.Level.INFO, sourceClass.getName(),
                sourceMethod, message, thrown);
    }

    public void debug(Class<?> sourceClass, String sourceMethod, String message) {
        realLogger.logp(java.util.logging.Level.FINE, sourceClass.getName(),
                sourceMethod, message);
    }

    public void debug(Class<?> sourceClass, String sourceMethod, String message,
                      Throwable thrown) {
        realLogger.logp(java.util.logging.Level.FINE, sourceClass.getName(),
                sourceMethod, message, thrown);
    }

    public void trace(Class<?> sourceClass, String sourceMethod, String message) {
        realLogger.logp(java.util.logging.Level.FINER, sourceClass.getName(),
                sourceMethod, message);
    }

    public void trace(Class<?> sourceClass, String sourceMethod, String message,
                      Throwable thrown) {
        realLogger.logp(java.util.logging.Level.FINER, sourceClass.getName(),
                sourceMethod, message, thrown);
    }

    public void warn(Class<?> sourceClass, String sourceMethod, String message) {
        realLogger.logp(java.util.logging.Level.WARNING, sourceClass.getName(),
                sourceMethod, message);
    }

    public void warn(Class<?> sourceClass, String sourceMethod, String message,
                     Throwable thrown) {
        realLogger.logp(java.util.logging.Level.WARNING, sourceClass.getName(),
                sourceMethod, message, thrown);
    }

    public void error(Class<?> sourceClass, String sourceMethod, String message) {
        realLogger.logp(java.util.logging.Level.SEVERE, sourceClass.getName(),
                sourceMethod, message);
    }

    public void error(Class<?> sourceClass, String sourceMethod, String message,
                      Throwable thrown) {
        realLogger.logp(java.util.logging.Level.SEVERE, sourceClass.getName(),
                sourceMethod, message, thrown);
    }

    public boolean isTraceEnabled() {
        return realLogger.isLoggable(java.util.logging.Level.FINER);
    }

    public boolean isDebugEnabled() {
        return realLogger.isLoggable(java.util.logging.Level.FINE)
                || realLogger.isLoggable(java.util.logging.Level.FINER);
    }

    public void enableDebug() {
        realLogger.setLevel(java.util.logging.Level.FINE);
    }

    public void disableDebug() {
        realLogger.setLevel(java.util.logging.Level.INFO);
    }
}
