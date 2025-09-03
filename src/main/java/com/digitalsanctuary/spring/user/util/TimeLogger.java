package com.digitalsanctuary.spring.user.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The TimeLogger class is a utility for logging the time taken for a process. It can log the time to a provided SLF4J Logger or to a default
 * logger if no Logger is provided.
 */
public class TimeLogger {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TimeLogger.class);
    
    private Logger logger;
    private String label = "";
    private long startTime;
    private long endTime;

    /**
     * Default constructor that initializes the TimeLogger and starts the timer.
     */
    public TimeLogger() {
        start();
    }

    /**
     * Constructor that initializes the TimeLogger with a provided SLF4J Logger and starts the timer.
     *
     * @param logger the SLF4J Logger to use for logging the time
     */
    public TimeLogger(Logger logger) {
        this.logger = logger;
        start();
    }

    /**
     * Constructor that initializes the TimeLogger with a provided SLF4J Logger and a label, then starts the timer.
     *
     * @param logger the SLF4J Logger to use for logging the time
     * @param label a label to include in the log message
     */
    public TimeLogger(Logger logger, String label) {
        this.logger = logger;
        this.label = label;
        start();
    }

    /**
     * Starts the timer by recording the current system time in milliseconds.
     */
    public void start() {
        startTime = System.currentTimeMillis();
    }

    /**
     * Ends the timer by recording the current system time in milliseconds and logs the time taken.
     */
    public void end() {
        endTime = System.currentTimeMillis();
        logTime();
    }

    /**
     * Logs the time taken between the start and end times. If a Logger is provided, it logs the message at the debug level. Otherwise, it uses
     * the default logger for the TimeLogger class.
     */
    public void logTime() {
        long duration = endTime - startTime;
        String logMessage = label + " took " + duration + " milliseconds";

        Logger loggerToUse = logger != null ? logger : DEFAULT_LOGGER;
        loggerToUse.debug(logMessage);
    }

}
