package com.digitalsanctuary.spring.user.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared utility methods for parsing WebAuthn authenticator transport strings.
 *
 * <p>
 * Transport values are stored as comma-separated strings in the database (e.g. {@code "internal,hybrid"}).
 * This class provides two levels of parsing:
 * </p>
 * <ul>
 * <li>{@link #parseTransportStrings(String)} - split into trimmed, non-empty strings (for DTOs)</li>
 * <li>{@link #parseTransports(String)} - map to {@link AuthenticatorTransport} enum values (for Spring Security)</li>
 * </ul>
 */
@Slf4j
public final class WebAuthnTransportUtils {

	private WebAuthnTransportUtils() {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Parse a comma-separated transport string into a list of trimmed, non-empty strings.
	 *
	 * @param transports the comma-separated transport string, may be {@code null} or empty
	 * @return an unmodifiable list of transport name strings, never {@code null}
	 */
	public static List<String> parseTransportStrings(String transports) {
		if (transports == null || transports.isEmpty()) {
			return Collections.emptyList();
		}
		return Arrays.stream(transports.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.collect(Collectors.toUnmodifiableList());
	}

	/**
	 * Parse a comma-separated transport string into a set of {@link AuthenticatorTransport} enum values.
	 *
	 * <p>
	 * Unknown transport values are logged at WARN level and skipped.
	 * </p>
	 *
	 * @param transports the comma-separated transport string, may be {@code null} or empty
	 * @return an unmodifiable set of transport enum values, never {@code null}
	 */
	public static Set<AuthenticatorTransport> parseTransports(String transports) {
		if (transports == null || transports.isEmpty()) {
			return Collections.emptySet();
		}
		return parseTransportStrings(transports).stream().map(value -> {
			try {
				return AuthenticatorTransport.valueOf(value);
			} catch (IllegalArgumentException e) {
				log.warn("Unknown AuthenticatorTransport '{}', skipping", value);
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
	}
}
