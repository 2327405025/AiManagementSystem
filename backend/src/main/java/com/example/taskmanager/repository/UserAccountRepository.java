package com.example.taskmanager.repository;

import com.example.taskmanager.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data equivalent of a MyBatis UserMapper: persistence only, no business rules.
 * 对应 MyBatis 里的 UserMapper：只负责持久化，不放业务规则。
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
