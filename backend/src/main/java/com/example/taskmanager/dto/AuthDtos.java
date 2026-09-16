package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Login and registration contracts. Passwords are never returned to clients.
 * 登录与注册契约。密码不会返回给客户端。
 */
public final class AuthDtos {
    private AuthDtos() {}

    public record AuthRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username may contain letters, digits and underscore only")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record AuthResponse(String token, Long userId, String username) {}
}
