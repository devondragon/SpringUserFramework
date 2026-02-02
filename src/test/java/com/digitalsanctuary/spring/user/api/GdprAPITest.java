package com.digitalsanctuary.spring.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.digitalsanctuary.spring.user.dto.ConsentRequestDto;
import com.digitalsanctuary.spring.user.dto.GdprExportDTO;
import com.digitalsanctuary.spring.user.gdpr.ConsentAuditService;
import com.digitalsanctuary.spring.user.gdpr.ConsentRecord;
import com.digitalsanctuary.spring.user.gdpr.ConsentType;
import com.digitalsanctuary.spring.user.gdpr.GdprConfig;
import com.digitalsanctuary.spring.user.gdpr.GdprDeletionService;
import com.digitalsanctuary.spring.user.gdpr.GdprExportService;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for GdprAPI REST controller.
 *
 * <p>These tests use standalone MockMvc setup with mocked services to test
 * the GDPR API endpoints in isolation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GdprAPI Unit Tests")
class GdprAPITest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GdprConfig gdprConfig;

    @Mock
    private GdprExportService gdprExportService;

    @Mock
    private GdprDeletionService gdprDeletionService;

    @Mock
    private ConsentAuditService consentAuditService;

    @Mock
    private UserService userService;

    @Mock
    private MessageSource messages;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GdprAPI gdprAPI;

    private User testUser;
    private DSUserDetails testUserDetails;

    /**
     * Custom argument resolver that returns a specific DSUserDetails or null.
     */
    private static class DSUserDetailsArgumentResolver implements HandlerMethodArgumentResolver {
        private final DSUserDetails userDetails;

        DSUserDetailsArgumentResolver(DSUserDetails userDetails) {
            this.userDetails = userDetails;
        }

        @Override
        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
            return parameter.getParameterType().equals(DSUserDetails.class);
        }

        @Override
        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                org.springframework.web.context.request.NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return userDetails;
        }
    }

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();

        testUserDetails = new DSUserDetails(testUser);

        // Default MockMvc with null user (unauthenticated)
        mockMvc = MockMvcBuilders.standaloneSetup(gdprAPI)
                .setCustomArgumentResolvers(new DSUserDetailsArgumentResolver(null))
                .build();
    }

    /**
     * Creates a MockMvc instance with a custom argument resolver that returns the test user.
     */
    private MockMvc mockMvcWithAuthenticatedUser() {
        return MockMvcBuilders.standaloneSetup(gdprAPI)
                .setCustomArgumentResolvers(new DSUserDetailsArgumentResolver(testUserDetails))
                .build();
    }

    /**
     * Creates a MockMvc instance with validation enabled and authenticated user.
     */
    private MockMvc mockMvcWithValidationAndAuth() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        return MockMvcBuilders.standaloneSetup(gdprAPI)
                .setValidator(validator)
                .setCustomArgumentResolvers(new DSUserDetailsArgumentResolver(testUserDetails))
                .build();
    }

    @Nested
    @DisplayName("Export User Data Tests")
    class ExportUserDataTests {

        @Test
        @DisplayName("GET /user/gdpr/export - returns export when authenticated")
        void exportUserData_whenAuthenticated_returnsExport() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithAuthenticatedUser();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);

            GdprExportDTO exportDTO = GdprExportDTO.builder()
                    .metadata(GdprExportDTO.ExportMetadata.builder()
                            .exportedAt(Instant.now())
                            .formatVersion("1.0")
                            .exportedBy("Spring User Framework")
                            .build())
                    .userData(GdprExportDTO.UserData.builder()
                            .id(testUser.getId())
                            .email(testUser.getEmail())
                            .firstName(testUser.getFirstName())
                            .lastName(testUser.getLastName())
                            .enabled(true)
                            .build())
                    .auditHistory(Collections.emptyList())
                    .consents(Collections.emptyList())
                    .build();
            when(gdprExportService.exportUserData(testUser)).thenReturn(exportDTO);

            // When & Then
            authedMockMvc.perform(get("/user/gdpr/export")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").exists());

            verify(gdprExportService).exportUserData(testUser);
        }

        @Test
        @DisplayName("GET /user/gdpr/export - returns 401 when unauthenticated")
        void exportUserData_whenUnauthenticated_returns401() throws Exception {
            // Given
            when(gdprConfig.isEnabled()).thenReturn(true);

            // When & Then
            mockMvc.perform(get("/user/gdpr/export")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(1));

            verify(gdprExportService, never()).exportUserData(any());
        }

        @Test
        @DisplayName("GET /user/gdpr/export - returns 404 when GDPR disabled")
        void exportUserData_whenGdprDisabled_returns404() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithAuthenticatedUser();
            when(gdprConfig.isEnabled()).thenReturn(false);

            // When & Then
            authedMockMvc.perform(get("/user/gdpr/export")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(404));

            verify(gdprExportService, never()).exportUserData(any());
        }
    }

    @Nested
    @DisplayName("Delete Account Tests")
    class DeleteAccountTests {

        @Test
        @DisplayName("POST /user/gdpr/delete - deletes and logs out when authenticated")
        void deleteAccount_whenAuthenticated_deletesAndLogsOut() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithAuthenticatedUser();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);

            GdprDeletionService.DeletionResult result = GdprDeletionService.DeletionResult.success(null);
            when(gdprDeletionService.deleteUser(testUser)).thenReturn(result);

            // When & Then
            authedMockMvc.perform(post("/user/gdpr/delete")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(0));

            verify(gdprDeletionService).deleteUser(testUser);
        }

        @Test
        @DisplayName("POST /user/gdpr/delete - returns 401 when unauthenticated")
        void deleteAccount_whenUnauthenticated_returns401() throws Exception {
            // Given
            when(gdprConfig.isEnabled()).thenReturn(true);

            // When & Then
            mockMvc.perform(post("/user/gdpr/delete")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(1));

            verify(gdprDeletionService, never()).deleteUser(any());
        }
    }

    @Nested
    @DisplayName("Record Consent Tests")
    class RecordConsentTests {

        @Test
        @DisplayName("POST /user/gdpr/consent - grants consent with valid request")
        void recordConsent_withValidRequest_grantsConsent() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithValidationAndAuth();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);

            ConsentRecord record = ConsentRecord.builder()
                    .type(ConsentType.MARKETING_EMAILS)
                    .grantedAt(Instant.now())
                    .method("api")
                    .build();
            when(consentAuditService.recordConsentGranted(any(), any(), any(), any(), any(), any()))
                    .thenReturn(record);

            ConsentRequestDto request = ConsentRequestDto.builder()
                    .consentType(ConsentType.MARKETING_EMAILS)
                    .grant(true)
                    .policyVersion("v1.0")
                    .method("api")
                    .build();

            // When & Then
            authedMockMvc.perform(post("/user/gdpr/consent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(0));

            verify(consentAuditService).recordConsentGranted(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("POST /user/gdpr/consent - handles custom type correctly")
        void recordConsent_withCustomType_validatesInput() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithValidationAndAuth();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);

            ConsentRecord record = ConsentRecord.builder()
                    .type(ConsentType.CUSTOM)
                    .customType("my_custom_consent")
                    .grantedAt(Instant.now())
                    .method("api")
                    .build();
            when(consentAuditService.recordConsentGranted(any(), any(), any(), any(), any(), any()))
                    .thenReturn(record);

            ConsentRequestDto request = ConsentRequestDto.builder()
                    .consentType(ConsentType.CUSTOM)
                    .customType("my_custom_consent")
                    .grant(true)
                    .build();

            // When & Then
            authedMockMvc.perform(post("/user/gdpr/consent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("POST /user/gdpr/consent - rejects invalid custom type pattern")
        void recordConsent_withInvalidCustomType_returns400() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithValidationAndAuth();

            ConsentRequestDto request = ConsentRequestDto.builder()
                    .consentType(ConsentType.CUSTOM)
                    .customType("invalid<script>type") // Contains invalid characters
                    .grant(true)
                    .build();

            // When & Then
            authedMockMvc.perform(post("/user/gdpr/consent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(consentAuditService, never()).recordConsentGranted(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("POST /user/gdpr/consent - rejects custom type exceeding max length")
        void recordConsent_withTooLongCustomType_returns400() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithValidationAndAuth();

            ConsentRequestDto request = ConsentRequestDto.builder()
                    .consentType(ConsentType.CUSTOM)
                    .customType("a".repeat(101)) // Exceeds 100 char limit
                    .grant(true)
                    .build();

            // When & Then
            authedMockMvc.perform(post("/user/gdpr/consent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(consentAuditService, never()).recordConsentGranted(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("POST /user/gdpr/consent - requires custom type when consent type is CUSTOM")
        void recordConsent_missingCustomType_returns400() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithValidationAndAuth();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);

            ConsentRequestDto request = ConsentRequestDto.builder()
                    .consentType(ConsentType.CUSTOM)
                    .customType(null) // Missing required custom type
                    .grant(true)
                    .build();

            // When & Then
            authedMockMvc.perform(post("/user/gdpr/consent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(2));

            verify(consentAuditService, never()).recordConsentGranted(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("POST /user/gdpr/consent - withdraws consent successfully")
        void recordConsent_withdrawConsent_succeeds() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithValidationAndAuth();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);

            ConsentRecord record = ConsentRecord.builder()
                    .type(ConsentType.MARKETING_EMAILS)
                    .withdrawnAt(Instant.now())
                    .method("api")
                    .build();
            when(consentAuditService.recordConsentWithdrawn(any(), any(), any(), any(), any()))
                    .thenReturn(record);

            ConsentRequestDto request = ConsentRequestDto.builder()
                    .consentType(ConsentType.MARKETING_EMAILS)
                    .grant(false) // Withdraw consent
                    .build();

            // When & Then
            authedMockMvc.perform(post("/user/gdpr/consent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(consentAuditService).recordConsentWithdrawn(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Get Consent Status Tests")
    class GetConsentStatusTests {

        @Test
        @DisplayName("GET /user/gdpr/consent - returns consent status when authenticated")
        void getConsentStatus_whenAuthenticated_returnsStatus() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithAuthenticatedUser();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);

            Map<String, ConsentAuditService.ConsentStatus> statusMap = new LinkedHashMap<>();
            statusMap.put("MARKETING_EMAILS", new ConsentAuditService.ConsentStatus("MARKETING_EMAILS", true, Instant.now(), null));
            statusMap.put("ANALYTICS", new ConsentAuditService.ConsentStatus("ANALYTICS", false, Instant.now(), Instant.now()));
            when(consentAuditService.getConsentStatus(testUser)).thenReturn(statusMap);

            // When & Then
            authedMockMvc.perform(get("/user/gdpr/consent")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").exists());

            verify(consentAuditService).getConsentStatus(testUser);
        }

        @Test
        @DisplayName("GET /user/gdpr/consent - returns 401 when unauthenticated")
        void getConsentStatus_whenUnauthenticated_returns401() throws Exception {
            // Given
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(gdprConfig.isConsentTracking()).thenReturn(true);

            // When & Then
            mockMvc.perform(get("/user/gdpr/consent")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(1));

            verify(consentAuditService, never()).getConsentStatus(any());
        }

        @Test
        @DisplayName("GET /user/gdpr/consent - returns 404 when consent tracking disabled")
        void getConsentStatus_whenConsentTrackingDisabled_returns404() throws Exception {
            // Given
            MockMvc authedMockMvc = mockMvcWithAuthenticatedUser();
            when(gdprConfig.isEnabled()).thenReturn(true);
            when(gdprConfig.isConsentTracking()).thenReturn(false);

            // When & Then
            authedMockMvc.perform(get("/user/gdpr/consent")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(404));

            verify(consentAuditService, never()).getConsentStatus(any());
        }
    }
}
