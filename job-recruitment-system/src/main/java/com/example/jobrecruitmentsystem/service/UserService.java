package com.example.jobrecruitmentsystem.service;

import com.example.jobrecruitmentsystem.model.Role;
import com.example.jobrecruitmentsystem.model.SeekerProfile; // <-- ADDED
import com.example.jobrecruitmentsystem.model.User;
import com.example.jobrecruitmentsystem.repository.RoleRepository;
import com.example.jobrecruitmentsystem.repository.SeekerProfileRepository; // <-- ADDED
import com.example.jobrecruitmentsystem.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Collection;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeekerProfileRepository seekerProfileRepository; // <--- ADDED

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       SeekerProfileRepository seekerProfileRepository) { // <--- MODIFIED
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.seekerProfileRepository = seekerProfileRepository; // <--- ADDED
    }

    // --- Core User Management ---

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getIsEnabled(),
                true, true, true,
                getAuthorities(user.getRole())
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Role role) {
        return Collections.singletonList(new SimpleGrantedAuthority(role.getName()));
    }

    @Transactional
    public User registerNewUser(User user, String roleName, String companyName) throws Exception {
        if (userRepository.findByUsername(user.getUsername()).isPresent() || userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new Exception("Username or Email already in use.");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(role);

        if ("EMPLOYER".equals(roleName)) {
            if (companyName == null || companyName.isBlank()) {
                throw new Exception("Company name is required for employers.");
            }
            // FIX: Set companyName directly on the user (Replaces Company entity logic)
            user.setCompanyName(companyName);
            // Default setup for other company fields
            user.setCompanyDescription("Please update your company profile.");
            user.setCompanyLocation("TBD");
            user.setCompanyLogoFilename(null);
            user.setIsEnabled(true);
        } else if ("JOB_SEEKER".equals(roleName)) { // <--- MODIFIED
            // Ensure no company data is left for non-employers
            user.setCompanyName(null);
            user.setIsEnabled(true);
        } else {
            user.setIsEnabled(false);
        }

        // 1. Save the User first to get its ID and ensure it exists
        User savedUser = userRepository.save(user);

        // 2. *** CRITICAL NEW LOGIC: Create and save SeekerProfile for JOB_SEEKER ***
        if ("JOB_SEEKER".equals(roleName)) {
            SeekerProfile profile = new SeekerProfile(savedUser);
            seekerProfileRepository.save(profile);
        }

        return savedUser; // Return the saved user
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    // *** NEW METHOD ***
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Transactional
    public User saveUser(User user) {
        // *** CRITICAL FIX: Use saveAndFlush to ensure immediate persistence ***
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public void updatePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for password reset."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}