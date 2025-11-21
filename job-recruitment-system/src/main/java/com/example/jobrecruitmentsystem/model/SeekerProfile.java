package com.example.jobrecruitmentsystem.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "seeker_profile")
@Getter @Setter @NoArgsConstructor
public class SeekerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private User user;

    private String city;
    private String preferredLocation;

    private String currentTitle;
    private String profileHeadline;
    private Integer yearsExperience;
    @Column(columnDefinition = "TEXT")
    private String skills;
    private String jobType;
    private BigDecimal expectedSalary;

    private String resumeFilename;

    @Column(name = "work_experience_json", columnDefinition = "TEXT")
    private String workExperienceJson = "[]";

    @Column(name = "education_json", columnDefinition = "TEXT")
    private String educationJson = "[]";

    @Column(name = "support_chat_log", columnDefinition = "TEXT")
    private String supportChatLog = "[]";

    @Column(name = "offering_services")
    private Boolean offeringServices = true;

    @Column(name = "completeness_score")
    private Integer completenessScore = 0;

    // --- NEW FIELD: Featured Candidate Flag ---
    @Column(name = "is_featured")
    private Boolean isFeatured = false;
    // -----------------------------------------

    public SeekerProfile(User user) {
        this.user = user;
        this.yearsExperience = 0;
    }

    public void calculateCompleteness() {
        int score = 0;
        if (currentTitle != null && !currentTitle.isBlank()) score += 10;
        if (profileHeadline != null && !profileHeadline.isBlank()) score += 10;
        if (user != null && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) score += 10;
        if (city != null && !city.isBlank()) score += 10;
        if (skills != null && !skills.isBlank()) score += 20;
        if (resumeFilename != null && !resumeFilename.isBlank()) score += 20;
        if (workExperienceJson != null && workExperienceJson.length() > 2) score += 10;
        if (educationJson != null && educationJson.length() > 2) score += 10;
        this.completenessScore = Math.min(score, 100);
    }

    // Manual getters/setters (optional if Lombok is working, but safe to keep)
    public String getSupportChatLog() { return supportChatLog; }
    public void setSupportChatLog(String supportChatLog) { this.supportChatLog = supportChatLog; }
    public Boolean getOfferingServices() { return offeringServices; }
    public void setOfferingServices(Boolean offeringServices) { this.offeringServices = offeringServices; }

    public Boolean getIsFeatured() { return isFeatured; }
    public void setIsFeatured(Boolean isFeatured) { this.isFeatured = isFeatured; }
}