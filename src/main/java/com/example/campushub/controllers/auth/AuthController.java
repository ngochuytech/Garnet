package com.example.campushub.controllers.auth;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.dtos.GoogleCodeRequest;
import com.example.campushub.dtos.auth.LoginDTO;
import com.example.campushub.dtos.auth.RegisterDTO;
import com.example.campushub.models.jpa.Token;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.LoginResponse;
import com.example.campushub.services.AuthService;
import com.example.campushub.services.GoogleAuthService;
import com.example.campushub.services.TokenService;
import com.example.campushub.services.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;
    private final AuthService authService;
    private final UserService userService;
    private final GoogleAuthService googleAuthService;

    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterDTO registerDTO) throws Exception {

        if (registerDTO.getPassword() != null && !registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "Passwords do not match"));
        }

        if (!registerDTO.getEmail().contains("@") || !registerDTO.getEmail().contains(".")) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "Invalid email format"));
        }

        authService.register(registerDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User registered successfully", null));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginDTO loginDTO) throws Exception {
        String token = authService.login(loginDTO);
        User user = userService.getUserFromEmail(loginDTO.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        tokenService.addToken(user, refreshToken, jwtTokenProvider.getJtiFromToken(refreshToken));

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("None")
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .token(token)
                .user(LoginResponse.UserResponse.builder()
                        .id(user.getId())
                        .fullname(user.getFullName())
                        .department(user.getDepartment())
                        .build())
                .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new ApiResponse<>(true, loginResponse, null));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(required = false) String refreshToken) throws Exception {
        if (refreshToken != null) {
            try {
                tokenService.revokeToken(refreshToken);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
            }
        }

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(new ApiResponse<>(true, "Logged out successfully", null));
    }

    @PostMapping("/seed-users")
    public ResponseEntity<?> seedUsers(@RequestParam(defaultValue = "1") int count){
        return ResponseEntity.ok(ApiResponse.ok("Đã tạo " + userService.seedUser(count) + " người dùng giả thành công"));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@CookieValue(required = false) String refreshToken) throws Exception {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token không tồn tại"));
        }

        Token tokenEntity = tokenService.findByToken(refreshToken);
        if (tokenEntity == null || tokenEntity.isRevoked()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token không hợp lệ hoặc đã bị thu hồi"));
        }

        User user = userService.getUserById(tokenEntity.getUser().getId());
        String newAccessToken = tokenService.refreshToken(user, tokenEntity.getToken());

        LoginResponse loginResponse = LoginResponse.builder()
                .token(newAccessToken)
                .user(LoginResponse.UserResponse.builder()
                        .id(user.getId())
                        .fullname(user.getFullName())
                        .department(user.getDepartment())
                        .build())
                .build();

        return ResponseEntity.ok(new ApiResponse<>(true, loginResponse, null));
    }

    @GetMapping("/social-login/google")
    public void socialAuth(HttpServletResponse response) throws IOException {
        String authorizationUrl = googleAuthService.generateAuthUrl();
        response.sendRedirect(authorizationUrl);
    }

    @PostMapping("/social-login/google/callback")
    public ResponseEntity<?> googleCallback(@RequestBody GoogleCodeRequest request, HttpServletResponse response)
            throws Exception {
        try {
            User user = googleAuthService.loginWithGoogle(request);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null,
                    user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String token = jwtTokenProvider.generateToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);
            tokenService.addToken(user, refreshToken, jwtTokenProvider.getJtiFromToken(refreshToken));

            ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(7 * 24 * 60 * 60)
                    .sameSite("None")
                    .build();

            LoginResponse loginResponse = LoginResponse.builder()
                    .token(token)
                    .user(LoginResponse.UserResponse.builder()
                            .id(user.getId())
                            .fullname(user.getFullName())
                            .department(user.getDepartment())
                            .build())
                    .build();
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(ApiResponse.ok(loginResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));

        }
    }
}
