package com.example.taskmanager.controller;

import com.example.taskmanager.dto.AuthDtos.AuthRequest;
import com.example.taskmanager.dto.AuthDtos.AuthResponse;
import com.example.taskmanager.security.AuthUser;
import com.example.taskmanager.security.CurrentUser;
import com.example.taskmanager.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public register/login plus a token introspection endpoint for the workspace.
 * 公开的注册/登录，以及工作台用来确认当前用户的接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    AuthResponse me() {
        AuthUser user = currentUser.require();
        return new AuthResponse(null, user.id(), user.username());
    }
}
