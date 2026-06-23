package com.example.campushub.controllers.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.models.jpa.User;
import com.example.campushub.services.AuthService;
import com.example.campushub.services.GoogleAuthService;
import com.example.campushub.services.TokenService;
import com.example.campushub.services.UserService;

@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = OAuth2ClientWebSecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private GoogleAuthService googleAuthService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void registerReturnsCreatedForValidPayload() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullname": "Test User",
                          "email": "test@example.com",
                          "password": "password123",
                          "confirmPassword": "password123"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).register(any());
    }

    @Test
    void registerRejectsMismatchedPasswordsBeforeCallingService() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullname": "Test User",
                          "email": "test@example.com",
                          "password": "password123",
                          "confirmPassword": "different123"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(authService, never()).register(any());
    }

    @Test
    void loginReturnsAccessTokenAndRefreshCookie() throws Exception {
        User user = User.builder()
                .id("user-1")
                .fullName("Test User")
                .email("test@example.com")
                .password("encoded-password")
                .department("Computer Science")
                .build();
        when(authService.login(any())).thenReturn("access-token");
        when(userService.getUserFromEmail("test@example.com")).thenReturn(user);
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("refresh-jti");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"test@example.com","password":"password123"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("access-token"))
                .andExpect(jsonPath("$.data.user.id").value("user-1"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")));

        verify(tokenService).addToken(user, "refresh-token", "refresh-jti");
    }

    @Test
    void loginRejectsMissingEmailAndPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(authService, never()).login(any());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        mockMvc.perform(post("/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(tokenService).revokeToken("refresh-token");
    }

}
