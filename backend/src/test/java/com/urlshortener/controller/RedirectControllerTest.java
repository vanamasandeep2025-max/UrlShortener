package com.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.dto.response.UrlRedirectTarget;
import com.urlshortener.events.UrlClickedEvent;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.service.UrlService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RedirectControllerTest {

    @Mock
    private UrlService urlService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RedirectController controller = new RedirectController(urlService, eventPublisher);
        ReflectionTestUtils.setField(controller, "baseUrl", "https://short.test");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void redirectsToDestinationAndPublishesClickEvent() throws Exception {
        UrlRedirectTarget target = new UrlRedirectTarget(UUID.randomUUID(), "ABC1234", "https://example.com", null, null);
        when(urlService.resolveForRedirect("ABC1234")).thenReturn(target);

        mockMvc.perform(get("/ABC1234").header("User-Agent", "JUnit"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com"));

        verify(eventPublisher).publishEvent(any(UrlClickedEvent.class));
    }

    @Test
    void returns410ForExpiredLinkAndDoesNotTrackClick() throws Exception {
        UrlRedirectTarget target = new UrlRedirectTarget(
            UUID.randomUUID(), "OLD0001", "https://example.com", null, Instant.now().minusSeconds(3600));
        when(urlService.resolveForRedirect("OLD0001")).thenReturn(target);

        mockMvc.perform(get("/OLD0001"))
            .andExpect(status().isGone());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void returns404ForUnknownCode() throws Exception {
        when(urlService.resolveForRedirect("MISSING")).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/MISSING"))
            .andExpect(status().isNotFound());
    }

    @Test
    void redirectsToPasswordPromptForProtectedLinkWithoutTrackingClick() throws Exception {
        UrlRedirectTarget target = new UrlRedirectTarget(
            UUID.randomUUID(), "PROT001", "https://example.com", "bcrypt-hash", null);
        when(urlService.resolveForRedirect("PROT001")).thenReturn(target);

        mockMvc.perform(get("/PROT001"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://short.test/protected.html?code=PROT001"));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
