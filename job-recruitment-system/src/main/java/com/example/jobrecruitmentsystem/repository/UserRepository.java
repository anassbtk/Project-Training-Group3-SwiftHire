package com.example.jobrecruitmentsystem.repository;

import com.example.jobrecruitmentsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a User entity by their unique username (used by Spring Security).
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a User entity by their unique email address.
     */
    Optional<User> findByEmail(String email);

    // Note: No other methods are required based on the current controller logic.
}