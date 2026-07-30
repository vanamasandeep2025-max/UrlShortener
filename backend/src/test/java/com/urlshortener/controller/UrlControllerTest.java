package com.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.dto.response.PageResponse;
import com.urlshortener.dto.response.UrlResponse;
import com.urlshortener.entity.UserRole;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.exception.InvalidUrlPasswordException;
import com.urlshortener.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UrlControllerTest {

    @Mock
    private com.urlshortener.service.UrlService urlService;

    private MockMvc mockMvc;
    private final AuthenticatedUser currentUser = new AuthenticatedUser(UUID.randomUUID(), "alice", UserRole.USER);

    @BeforeEach
    void setUp() {
        UrlController controller = new UrlController(urlService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role()));
        var authentication = new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUrlReturns201WithBody() throws Exception {
        when(urlService.createUrl(any(), eq(currentUser.id())))
            .thenReturn(UrlResponse.builder().shortCode("ABC1234").shortUrl("http://short.test/ABC1234").build());

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/a/b/c\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("ABC1234"));
    }

    @Test
    void createUrlRejectsNonHttpUrlWithValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"javascript:alert(1)\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createUrlRejectsBlankUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listUrlsReturnsPagedResults() throws Exception {
        PageResponse<UrlResponse> page = PageResponse.<UrlResponse>builder()
            .content(List.of(UrlResponse.builder().shortCode("ABC1234").build()))
            .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true).build();
        when(urlService.listUrls(eq(currentUser.id()), eq(false), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/urls"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].shortCode").value("ABC1234"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deleteUrlReturns204AndDelegatesToService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/urls/" + id))
            .andExpect(status().isNoContent());

        verify(urlService).softDelete(id, currentUser.id(), false);
    }

    @Test
    void verifyPasswordReturns401OnWrongPassword() throws Exception {
        when(urlService.verifyPasswordAndGetDestination(eq("ABC1234"), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/urls/ABC1234/verify-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyPasswordReturns200WithDestinationOnSuccess() throws Exception {
        when(urlService.verifyPasswordAndGetDestination(eq("ABC1234"), eq("correct")))
            .thenReturn(Optional.of("https://example.com"));

        mockMvc.perform(post("/api/v1/urls/ABC1234/verify-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"correct\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void globalExceptionHandlerTranslatesInvalidPasswordExceptionTo401() throws Exception {
        // Sanity check that the standalone MockMvc's controller advice is actually wired
        // (verifyPassword throws InvalidUrlPasswordException on empty Optional; see above test).
        when(urlService.verifyPasswordAndGetDestination(anyString(), anyString()))
            .thenThrow(new InvalidUrlPasswordException("bad"));

        mockMvc.perform(post("/api/v1/urls/XYZ/verify-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }
}
