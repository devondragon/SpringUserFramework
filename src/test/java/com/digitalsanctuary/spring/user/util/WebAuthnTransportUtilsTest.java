package com.digitalsanctuary.spring.user.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;

@DisplayName("WebAuthnTransportUtils Tests")
class WebAuthnTransportUtilsTest {

	@Nested
	@DisplayName("Utility Class Construction")
	class ConstructionTests {

		@Test
		@DisplayName("should throw IllegalStateException when instantiated via reflection")
		void shouldThrowOnInstantiation() throws Exception {
			Constructor<WebAuthnTransportUtils> constructor = WebAuthnTransportUtils.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			assertThatThrownBy(() -> {
				try {
					constructor.newInstance();
				} catch (InvocationTargetException e) {
					throw e.getCause();
				}
			}).isInstanceOf(IllegalStateException.class).hasMessage("Utility class");
		}
	}

	@Nested
	@DisplayName("parseTransportStrings")
	class ParseTransportStringsTests {

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("should return empty list for null or empty input")
		void shouldReturnEmptyForNullOrEmpty(String input) {
			assertThat(WebAuthnTransportUtils.parseTransportStrings(input)).isEmpty();
		}

		@Test
		@DisplayName("should parse single transport")
		void shouldParseSingleTransport() {
			assertThat(WebAuthnTransportUtils.parseTransportStrings("usb")).containsExactly("usb");
		}

		@Test
		@DisplayName("should parse multiple transports")
		void shouldParseMultipleTransports() {
			List<String> result = WebAuthnTransportUtils.parseTransportStrings("usb,nfc,ble");
			assertThat(result).containsExactlyInAnyOrder("usb", "nfc", "ble");
		}

		@Test
		@DisplayName("should trim whitespace from each transport")
		void shouldTrimWhitespace() {
			List<String> result = WebAuthnTransportUtils.parseTransportStrings(" usb , nfc ");
			assertThat(result).containsExactlyInAnyOrder("usb", "nfc");
		}

		@Test
		@DisplayName("should filter empty entries from leading/trailing commas")
		void shouldFilterEmptyEntries() {
			assertThat(WebAuthnTransportUtils.parseTransportStrings(",usb,")).containsExactly("usb");
		}

		@Test
		@DisplayName("should return unmodifiable list")
		void shouldReturnUnmodifiableList() {
			List<String> result = WebAuthnTransportUtils.parseTransportStrings("usb");
			assertThatThrownBy(() -> result.add("nfc")).isInstanceOf(UnsupportedOperationException.class);
		}
	}

	@Nested
	@DisplayName("parseTransports")
	class ParseTransportsTests {

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("should return empty set for null or empty input")
		void shouldReturnEmptyForNullOrEmpty(String input) {
			assertThat(WebAuthnTransportUtils.parseTransports(input)).isEmpty();
		}

		@Test
		@DisplayName("should parse single transport value")
		void shouldParseSingleTransport() {
			String value = AuthenticatorTransport.USB.getValue();
			Set<AuthenticatorTransport> result = WebAuthnTransportUtils.parseTransports(value);
			assertThat(result).containsExactly(AuthenticatorTransport.USB);
		}

		@Test
		@DisplayName("should parse multiple transport values")
		void shouldParseMultipleTransports() {
			String input = AuthenticatorTransport.USB.getValue() + "," + AuthenticatorTransport.NFC.getValue();
			Set<AuthenticatorTransport> result = WebAuthnTransportUtils.parseTransports(input);
			assertThat(result).containsExactlyInAnyOrder(AuthenticatorTransport.USB, AuthenticatorTransport.NFC);
		}

		@Test
		@DisplayName("should trim whitespace from each transport value")
		void shouldTrimWhitespace() {
			String input = " " + AuthenticatorTransport.USB.getValue() + " ";
			Set<AuthenticatorTransport> result = WebAuthnTransportUtils.parseTransports(input);
			assertThat(result).containsExactly(AuthenticatorTransport.USB);
		}

		@Test
		@DisplayName("should return unmodifiable set")
		void shouldReturnUnmodifiableSet() {
			String validValue = AuthenticatorTransport.USB.getValue();
			Set<AuthenticatorTransport> result = WebAuthnTransportUtils.parseTransports(validValue);
			assertThatThrownBy(() -> result.add(AuthenticatorTransport.NFC))
					.isInstanceOf(UnsupportedOperationException.class);
		}
	}
}
