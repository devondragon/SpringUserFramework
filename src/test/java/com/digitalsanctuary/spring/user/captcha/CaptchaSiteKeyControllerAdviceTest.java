package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaptchaSiteKeyControllerAdvice")
class CaptchaSiteKeyControllerAdviceTest {

    @Mock
    private ObjectProvider<CaptchaService> captchaServiceProvider;

    @Mock
    private CaptchaService captchaService;

    @Controller
    static class TestPageController {
        @GetMapping("/captcha-advice-test-page")
        public String page() {
            return "test";
        }
    }

    @Test
    void shouldExposeSiteKeyModelAttributeWhenServiceAvailable() throws Exception {
        when(captchaServiceProvider.getIfAvailable()).thenReturn(captchaService);
        when(captchaService.getSiteKey()).thenReturn("the-site-key");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestPageController())
                .setControllerAdvice(new CaptchaSiteKeyControllerAdvice(captchaServiceProvider)).build();

        mockMvc.perform(get("/captcha-advice-test-page"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("captchaSiteKey", "the-site-key"));
    }

    @Test
    void shouldExposeNullSiteKeyWhenServiceUnavailable() throws Exception {
        when(captchaServiceProvider.getIfAvailable()).thenReturn(null);
        CaptchaSiteKeyControllerAdvice advice = new CaptchaSiteKeyControllerAdvice(captchaServiceProvider);

        assertThat(advice.captchaSiteKey()).isNull();
    }
}
