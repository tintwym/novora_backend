package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.AuthDtos;
import prod.tint_wym.novora_backend.service.AuthService;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping({"/api/auth/register", "/auth/register"})
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return authService.register(request, httpRequest, httpResponse);
    }

    /**
     * Creates a Novora workspace for a Firebase-authenticated user. The client must already have
     * signed up / signed in with Firebase and send {@code Authorization: Bearer <idToken>}.
     */
    @PostMapping({"/api/auth/firebase/register", "/auth/firebase/register"})
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse registerWithFirebase(
            @Valid @RequestBody AuthDtos.FirebaseRegisterRequest request,
            Authentication authentication) {
        return authService.registerWithFirebase(request, authentication);
    }

    @PostMapping({"/api/auth/login", "/auth/login"})
    public AuthDtos.AuthResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return authService.login(request, httpRequest, httpResponse);
    }

    @GetMapping({"/api/me", "/auth/me"})
    public AuthDtos.AuthResponse me(
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return authService.me(authentication, httpRequest, httpResponse);
    }
}
