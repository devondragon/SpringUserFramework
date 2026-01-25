package com.digitalsanctuary.spring.user.audit;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring event listener that processes {@link AuditEvent} instances asynchronously.
 *
 * <p>This component listens for audit events and delegates the writing of event data
 * to an {@link AuditLogWriter} implementation. The processing is asynchronous to avoid
 * impacting application performance.
 *
 * <p>The listener only processes events when audit logging is enabled via
 * {@code AuditConfig.isLogEvents()}. All exceptions are caught and logged to ensure
 * audit failures never impact application flow.
 *
 * @see AuditEvent
 * @see AuditLogWriter
 * @see AuditConfig
 */
@Slf4j
@Async
@Component
public class AuditEventListener {

	private final AuditConfig auditConfig;

	private final AuditLogWriter auditLogWriter;

	/**
	 * Creates a new AuditEventListener with the required dependencies.
	 *
	 * @param auditConfig the audit configuration
	 * @param auditLogWriter the audit log writer
	 */
	public AuditEventListener(AuditConfig auditConfig, AuditLogWriter auditLogWriter) {
		this.auditConfig = auditConfig;
		this.auditLogWriter = auditLogWriter;
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
		try {
			log.debug("AuditEventListener.onApplicationEvent: called with event: {}", event);
			if (auditConfig.isLogEvents() && event != null) {
				log.debug("AuditEventListener.onApplicationEvent: logging event...");
				auditLogWriter.writeLog(event);
			}
		} catch (Exception e) {
			// Never let audit failures impact application flow
			log.error("AuditEventListener.onApplicationEvent: Failed to process audit event (suppressed): {}", e.getMessage(), e);
		}
	}
}
