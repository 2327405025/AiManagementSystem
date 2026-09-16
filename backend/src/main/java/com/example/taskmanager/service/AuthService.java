package com.example.taskmanager.service;

import com.example.taskmanager.domain.UserAccount;
import com.example.taskmanager.dto.AuthDtos.AuthRequest;
import com.example.taskmanager.dto.AuthDtos.AuthResponse;
import com.example.taskmanager.exception.ApiException;
import com.example.taskmanager.repository.UserAccountRepository;
import com.example.taskmanager.security.AuthUser;
import com.example.taskmanager.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login. Password hashing stays here, never in the controller.
 * 注册与登录。密码哈希只在服务层处理，不进入 Controller。
 */
@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username is already taken");
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        users.saveAndFlush(account);
        return token(account);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        UserAccount account = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return token(account);
    }

    private AuthResponse token(UserAccount account) {
        String jwt = jwtService.issue(new AuthUser(account.getId(), account.getUsername()));
        return new AuthResponse(jwt, account.getId(), account.getUsername());
    }
}
