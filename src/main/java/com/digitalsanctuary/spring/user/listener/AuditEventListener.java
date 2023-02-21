package com.digitalsanctuary.spring.user.listener;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * This class processes AuditEvents. This class writes the AuditEvent data to a text file on the server. You could easily change the logic to write to
 * a database, send events to a REST API, or anything else.
 *
 * @see AuditEvent
 */
@Slf4j
@Async
@Component
public class AuditEventListener {

	/** The logEvents flag. Set to true to log audit events. */
	@Value("${user.audit.logEvents:false}")
	private boolean logEvents;

	/** The audit log file path. */
	@Value("${user.audit.logFilePath:}")
	private String logFilePath;

	/**
	 * The flush on write flag, if enabled, causes the BufferedWriter to be flushed on every log entry. This has a performance impact under heavy
	 * loads, but ensures events are written to the log file without delay. This is beneficial in development environments, or environments where the
	 * performance penalty is less important that ensuring events are not lost in case of JVM or server crash.
	 */
	@Value("${user.audit.flushOnWrite:false}")
	private boolean flushOnWrite;

	/** The buffered writer. This gets instantiated by the setup method. */
	private BufferedWriter bufferedWriter;

	/**
	 * Setup the service, opening the log file for writing, and if the file is new, write a header line first.
	 */
	@PostConstruct
	private void setup() {
		log.info("AuditEventListener.setup: Entering...");
		if (logEvents) {
			if (!StringUtils.hasText(logFilePath)) {
				log.error("AuditEventListener.setup: user.audit.logEvents is true, but no user.audit.logFilePath has been configured!");
			} else {
				log.debug("AuditEventListener.setup: Opening log file: {}", logFilePath);
				try {
					OpenOption[] fileOptions = {StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE};
					boolean newFile = false;
					if (Files.notExists(Path.of(logFilePath))) {
						newFile = true;
					}
					bufferedWriter = Files.newBufferedWriter(Path.of(logFilePath), fileOptions);
					if (newFile) {
						writeHeader();
					}
					log.info("AuditEventListener.setup: Log file opened.");
				} catch (IOException e) {
					log.error("AuditEventListener.setup: IOException trying to open log file: {}", logFilePath, e);
				}
			}
		}
	}

	/**
	 * Write a field header line to the start of a log file.
	 */
	private void writeHeader() {
		log.debug("AuditEventListener.writeHeader: writing header.");
		if (bufferedWriter != null) {
			String output = MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}", "Date", "Action", "Action Status", "User ID", "Email",
					"IP Address", "SessionId", "Message", "User Agent", "Extra Data");
			try {
				bufferedWriter.write(output);
				bufferedWriter.newLine();
				bufferedWriter.flush();
			} catch (IOException e) {
				log.error("AuditEventListener.onApplicationEvent: IOException writing line: {}", output, e);
			}
		}
	}

	/**
	 * Teardown the service, closing the file writer.
	 */
	@PreDestroy
	public void teardown() {
		if (logEvents) {
			if (bufferedWriter != null) {
				log.debug("AuditEventListener.teardown: Closing log file: {}", logFilePath);
				try {
					bufferedWriter.close();
					log.debug("AuditEventListener.teardown: Log file closed.");
				} catch (IOException e) {
					log.error("AuditEventListener.teardown: IOException while trying to close bufferedWriter!", e);
				}
			}
		}
	}

	/**
	 * Flush writer on schedule to balance performance with getting data written to the audit log.
	 */
	@Scheduled(fixedDelay = 30000, initialDelay = 30000)
	public void flushWriterOnSchedule() {
		if (bufferedWriter != null && !flushOnWrite) {
			try {
				bufferedWriter.flush();
			} catch (IOException e) {
				log.error("AuditEventListener.flushWriterOnSchedule: IOException flushing buffer!", e);
			}
		}
	}

	/**
	 * Handle the AuditEvents.
	 *
	 * In this case we are writing the event data out to an audit log on the server, using pipe delimiters.
	 *
	 * @param event the event
	 */
	@EventListener
	public void onApplicationEvent(AuditEvent event) {
		log.debug("AuditEventListener.onApplicationEvent: called with event: {}", event);
		if (logEvents && bufferedWriter != null && event != null) {
			log.debug("AuditEventListener.onApplicationEvent: logging event...");
			String userId = null;
			String userEmail = null;
			// If the event has a User object on it, we'll get some data from it
			if (event.getUser() != null) {
				userId = event.getUser().getId().toString();
				userEmail = event.getUser().getEmail();
			}
			String output = MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}", event.getDate(), event.getAction(),
					event.getActionStatus(), userId, userEmail, event.getIpAddress(), event.getSessionId(), event.getMessage(), event.getUserAgent(),
					event.getExtraData());

			log.debug("AuditEventListener.onApplicationEvent: output: {}", output);
			try {
				bufferedWriter.write(output);
				bufferedWriter.newLine();
				if (flushOnWrite) {
					bufferedWriter.flush();
				}
			} catch (IOException e) {
				log.error("AuditEventListener.onApplicationEvent: IOException writing line: {}", output, e);
			}
		}
	}
}
