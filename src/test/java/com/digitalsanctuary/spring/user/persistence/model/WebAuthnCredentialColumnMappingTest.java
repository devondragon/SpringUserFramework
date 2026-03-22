package com.digitalsanctuary.spring.user.persistence.model;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import org.hibernate.Length;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("WebAuthnCredential Column Mapping Tests")
class WebAuthnCredentialColumnMappingTest {

	@ParameterizedTest
	@ValueSource(strings = {"attestationObject", "attestationClientDataJson", "publicKey"})
	@DisplayName("should use Length.LONG32 on byte[] fields for cross-database BLOB compatibility")
	void shouldUseLengthLong32OnBlobFields(String fieldName) throws NoSuchFieldException {
		Field field = WebAuthnCredential.class.getDeclaredField(fieldName);
		Column column = field.getAnnotation(Column.class);
		assertThat(column)
				.as("Field '%s' must have @Column annotation", fieldName)
				.isNotNull();
		assertThat(column.length())
				.as("Field '%s' @Column length must be Length.LONG32 (%d) to auto-upgrade "
						+ "to LONGBLOB on MariaDB/MySQL and remain bytea on PostgreSQL",
						fieldName, Length.LONG32)
				.isEqualTo(Length.LONG32);
	}
}
