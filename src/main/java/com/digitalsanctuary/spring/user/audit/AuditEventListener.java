package com.digitalsanctuary.spring.user.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
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
 * <p>The {@link AuditLogWriter} dependency is resolved lazily through an
 * {@link ObjectProvider}. This is deliberate: the library's default writer bean
 * ({@link FileAuditLogWriter}) is gated by {@code user.audit.logEvents} in
 * {@link AuditMailAutoConfiguration}, so when audit logging is disabled (and no consumer
 * supplies their own writer) no {@link AuditLogWriter} bean exists. Injecting the writer
 * directly would then fail the application context startup with an
 * {@code UnsatisfiedDependencyException}. By holding an {@link ObjectProvider} and
 * resolving the writer only when an event is actually logged, this listener always starts
 * cleanly and simply short-circuits on the {@code logEvents} flag (with a null guard as a
 * belt-and-suspenders safety) when no writer is available.
 *
 * @see AuditEvent
 * @see AuditLogWriter
 * @see AuditConfig
 */
@Slf4j
@Async
@Component
@RequiredArgsConstructor
public class AuditEventListener {

	private final AuditConfig auditConfig;

	private final ObjectProvider<AuditLogWriter> auditLogWriterProvider;

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
				AuditLogWriter auditLogWriter = auditLogWriterProvider.getIfAvailable();
				if (auditLogWriter != null) {
					log.debug("AuditEventListener.onApplicationEvent: logging event...");
					auditLogWriter.writeLog(event);
				} else {
					log.debug("AuditEventListener.onApplicationEvent: no AuditLogWriter available; skipping event.");
				}
			}
		} catch (Exception e) {
			// Never let audit failures impact application flow
			log.error("AuditEventListener.onApplicationEvent: Failed to process audit event (suppressed): {}", e.getMessage(), e);
		}
	}
}
