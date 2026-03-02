package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;

@ServiceTest
@DisplayName("MfaConfiguration Unit Tests")
class MfaConfigurationTest {

	private MfaConfigProperties mfaConfigProperties;
	private WebAuthnConfigProperties webAuthnConfigProperties;
	private MfaConfiguration mfaConfiguration;

	@BeforeEach
	void setUp() {
		mfaConfigProperties = new MfaConfigProperties();
		webAuthnConfigProperties = new WebAuthnConfigProperties();
		mfaConfiguration = new MfaConfiguration(mfaConfigProperties, webAuthnConfigProperties);
	}

	@Nested
	@DisplayName("mapFactorToAuthority")
	class MapFactorToAuthority {

		@Test
		@DisplayName("should map PASSWORD to FactorGrantedAuthority.PASSWORD_AUTHORITY")
		void shouldMapToPasswordAuthorityWhenPasswordFactorGiven() {
			assertThat(MfaConfiguration.mapFactorToAuthority("PASSWORD"))
					.isEqualTo(FactorGrantedAuthority.PASSWORD_AUTHORITY);
		}

		@Test
		@DisplayName("should map WEBAUTHN to FactorGrantedAuthority.WEBAUTHN_AUTHORITY")
		void shouldMapToWebauthnAuthorityWhenWebauthnFactorGiven() {
			assertThat(MfaConfiguration.mapFactorToAuthority("WEBAUTHN"))
					.isEqualTo(FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
		}

		@Test
		@DisplayName("should be case-insensitive")
		void shouldMapCorrectlyWhenFactorNameIsLowercase() {
			assertThat(MfaConfiguration.mapFactorToAuthority("password"))
					.isEqualTo(FactorGrantedAuthority.PASSWORD_AUTHORITY);
			assertThat(MfaConfiguration.mapFactorToAuthority("webauthn"))
					.isEqualTo(FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
		}

		@Test
		@DisplayName("should return null for unknown factor")
		void shouldReturnNullWhenFactorIsUnknown() {
			assertThat(MfaConfiguration.mapFactorToAuthority("UNKNOWN")).isNull();
		}
	}

	@Nested
	@DisplayName("resolveFactorAuthorities")
	class ResolveFactorAuthorities {

		@Test
		@DisplayName("should resolve configured factors to authority strings")
		void shouldResolveAuthoritiesWhenFactorsConfigured() {
			mfaConfigProperties.setFactors(List.of("PASSWORD", "WEBAUTHN"));
			List<String> authorities = mfaConfiguration.resolveFactorAuthorities();
			assertThat(authorities).containsExactly(
					FactorGrantedAuthority.PASSWORD_AUTHORITY,
					FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
		}

		@Test
		@DisplayName("should return empty list for empty factors")
		void shouldReturnEmptyListWhenNoFactorsConfigured() {
			mfaConfigProperties.setFactors(List.of());
			assertThat(mfaConfiguration.resolveFactorAuthorities()).isEmpty();
		}

		@Test
		@DisplayName("should skip unknown factors")
		void shouldSkipUnknownWhenMixedFactorsConfigured() {
			mfaConfigProperties.setFactors(List.of("PASSWORD", "UNKNOWN"));
			List<String> authorities = mfaConfiguration.resolveFactorAuthorities();
			assertThat(authorities).containsExactly(FactorGrantedAuthority.PASSWORD_AUTHORITY);
		}
	}

	@Nested
	@DisplayName("validateMfaConfiguration")
	class ValidateMfaConfiguration {

		@Test
		@DisplayName("should not validate when MFA is disabled")
		void shouldNotValidateWhenDisabled() {
			mfaConfigProperties.setEnabled(false);
			// Should not throw
			mfaConfiguration.validateMfaConfiguration(null);
		}

		@Test
		@DisplayName("should throw when MFA enabled but no factors configured")
		void shouldThrowWhenNoFactorsConfigured() {
			mfaConfigProperties.setEnabled(true);
			mfaConfigProperties.setFactors(List.of());
			assertThatThrownBy(() -> mfaConfiguration.validateMfaConfiguration(null))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("no factors are configured");
		}

		@Test
		@DisplayName("should throw when unknown factor is configured")
		void shouldThrowWhenUnknownFactorConfigured() {
			mfaConfigProperties.setEnabled(true);
			mfaConfigProperties.setFactors(List.of("OTP"));
			assertThatThrownBy(() -> mfaConfiguration.validateMfaConfiguration(null))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Unknown MFA factor: 'OTP'");
		}

		@Test
		@DisplayName("should throw when WEBAUTHN factor configured but WebAuthn is disabled")
		void shouldThrowWhenWebauthnFactorButWebauthnDisabled() {
			mfaConfigProperties.setEnabled(true);
			mfaConfigProperties.setFactors(List.of("PASSWORD", "WEBAUTHN"));
			webAuthnConfigProperties.setEnabled(false);
			assertThatThrownBy(() -> mfaConfiguration.validateMfaConfiguration(null))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("WebAuthn is disabled");
		}

		@Test
		@DisplayName("should not throw when WEBAUTHN factor configured and WebAuthn is enabled")
		void shouldNotThrowWhenWebauthnFactorAndWebauthnEnabled() {
			mfaConfigProperties.setEnabled(true);
			mfaConfigProperties.setFactors(List.of("PASSWORD", "WEBAUTHN"));
			webAuthnConfigProperties.setEnabled(true);
			// Should not throw
			mfaConfiguration.validateMfaConfiguration(null);
		}

		@Test
		@DisplayName("should not throw for PASSWORD-only configuration")
		void shouldNotThrowWhenPasswordOnlyConfigured() {
			mfaConfigProperties.setEnabled(true);
			mfaConfigProperties.setFactors(List.of("PASSWORD"));
			// Should not throw (but will log a warning)
			mfaConfiguration.validateMfaConfiguration(null);
		}
	}
}
