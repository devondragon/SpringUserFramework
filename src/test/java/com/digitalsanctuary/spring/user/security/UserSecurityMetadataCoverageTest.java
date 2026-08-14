package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("Generated metadata covers the retired hand-maintained user.security keys")
class UserSecurityMetadataCoverageTest {

    private static String canonical(String name) {
        return name.toLowerCase().replace("-", "");
    }

    @Test
    void generatedMetadataContainsEveryRetiredKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> legacy = mapper.readValue(
                new ClassPathResource("metadata/legacy-user-security-keys.json").getInputStream(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));

        JsonNode generated = mapper.readTree(
                new ClassPathResource("META-INF/spring-configuration-metadata.json").getInputStream());
        Set<String> generatedNames = generated.get("properties").findValuesAsText("name").stream()
                .map(UserSecurityMetadataCoverageTest::canonical).collect(Collectors.toSet());

        assertThat(legacy.stream().map(UserSecurityMetadataCoverageTest::canonical))
                .allMatch(generatedNames::contains);
    }
}
