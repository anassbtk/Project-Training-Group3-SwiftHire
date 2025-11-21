package com.example.jobrecruitmentsystem.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "user")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 100)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    // *** NEW FIELD TO STORE THE CHOSEN QUESTION ***
    @Column(name = "security_question", length = 255)
    private String securityQuestion;

    @Column(name = "security_answer", length = 255)
    private String securityAnswer;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "first_name", length = 45)
    private String firstName;

    @Column(name = "last_name", length = 45)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_picture_filename", length = 255)
    private String profilePictureFilename;

    @Column(name = "is_enabled")
    private Boolean isEnabled = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER) // Role MUST be EAGER for security to read it
    @JoinColumn(name = "role_id", nullable = true) // Set to nullable to be safe
    private Role role;

    // --- ADDED: PREMIUM TIER FIELD ---
    @Column(name = "premium_tier", length = 20)
    private String premiumTier = "BASIC";
    // ---------------------------------

    // --- NEW COMPANY FIELDS (Replaces deleted Company entity link) ---
    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "company_description", columnDefinition = "TEXT")
    private String companyDescription;

    @Column(name = "company_location", length = 255)
    private String companyLocation;

    @Column(name = "company_logo_filename", length = 255)
    private String companyLogoFilename;
    // --- END NEW COMPANY FIELDS ---

    // *** ADDED: FIELD for Employer-to-Admin chat ***
    @Column(name = "admin_support_chat_log", columnDefinition = "TEXT")
    private String adminSupportChatLog = "[]";

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SeekerProfile seekerProfile;

    // --- Manual Getters/Setters (if not using Lombok consistently) ---
    // Note: Lombok @Getter/@Setter handles all fields, but manual methods are kept for safety.

    public String getAdminSupportChatLog() {
        return adminSupportChatLog;
    }

    public void setAdminSupportChatLog(String adminSupportChatLog) {
        this.adminSupportChatLog = adminSupportChatLog;
    }

    // ADDED: Manual getter/setter for premiumTier for consistency
    public String getPremiumTier() {
        return premiumTier;
    }

    public void setPremiumTier(String premiumTier) {
        this.premiumTier = premiumTier;
    }
}