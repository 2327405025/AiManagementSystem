package com.example.taskmanager.security;

/**
 * Authenticated principal stored in the security context.
 * 存放在安全上下文中的已登录主体。
 */
public record AuthUser(Long id, String username) {}
