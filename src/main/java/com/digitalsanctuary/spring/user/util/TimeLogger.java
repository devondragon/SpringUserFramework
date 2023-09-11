package com.digitalsanctuary.spring.user.util;

import org.slf4j.Logger;

public class TimeLogger {

    private Logger logger;
    private String label = "";
    private long startTime;
    private long endTime;

    public TimeLogger() {
        start();
    }

    public TimeLogger(Logger logger) {
        this.logger = logger;
        start();
    }

    public TimeLogger(Logger logger, String label) {
        this.logger = logger;
        this.label = label;
        start();
    }

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void end() {
        endTime = System.currentTimeMillis();
        logTime();
    }

    public void logTime() {
        long duration = endTime - startTime;
        String logMessage = label + " took " + duration + " milliseconds";

        if (logger == null) {
            System.out.println(logMessage);
        } else {
            logger.debug(logMessage);
        }
    }

}
