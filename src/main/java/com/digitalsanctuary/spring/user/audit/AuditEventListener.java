package com.digitalsanctuary.spring.user.audit;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AuditEventListener {

	private final AuditConfig auditConfig;

	private final AuditLogWriter auditLogWriter;

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
		if (auditConfig.isLogEvents() && event != null) {
			log.debug("AuditEventListener.onApplicationEvent: logging event...");
			auditLogWriter.writeLog(event);
		}
	}
}
