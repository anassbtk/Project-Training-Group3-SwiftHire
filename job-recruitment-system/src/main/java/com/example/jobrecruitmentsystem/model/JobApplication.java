package com.example.jobrecruitmentsystem.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate; // **** IMPORT THIS ****
import java.time.LocalDateTime;

@Entity
@Table(name = "job_application")
@Getter
@Setter
@NoArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seeker_id", nullable = false)
    private User seeker;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_id", nullable = false)
    private JobRequirement job;

    private String status = "APPLIED";

    @Column(name = "applied_at")
    private LocalDateTime appliedAt = LocalDateTime.now();

    @Column(name = "message_log", columnDefinition = "TEXT")
    private String messageLog = "[]";

    // ############ NEW FIELDS FOR EXAM & OFFER ############
    @Column(name = "exam_questions", columnDefinition = "TEXT")
    private String examQuestions;

    @Column(name = "exam_answers", columnDefinition = "TEXT")
    private String examAnswers;

    @Column(name = "exam_submitted")
    private Boolean examSubmitted = false;

    @Column(name = "exam_score")
    private Integer examScore = 0;

    @Column(name = "offer_start_date")
    private LocalDate offerStartDate;

    @Column(name = "offer_start_time")
    private String offerStartTime;

    @Column(name = "offer_location")
    private String offerLocation;

    @Column(name = "offer_required_papers", columnDefinition = "TEXT")
    private String offerRequiredPapers;
    // ######################################################


    public JobApplication(User seeker, JobRequirement job) {
        this.seeker = seeker;
        this.job = job;
    }

    // --- Getters and Setters (Lombok handles most, but we keep manual ones for safety) ---

    public User getSeeker() {
        return seeker;
    }

    public void setSeeker(User seeker) {
        this.seeker = seeker;
    }

    public JobRequirement getJob() {
        return job;
    }

    public void setJob(JobRequirement job) {
        this.job = job;
    }

    public String getMessageLog() {
        return messageLog;
    }

    public void setMessageLog(String messageLog) {
        this.messageLog = messageLog;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    // ############ GETTERS/SETTERS FOR NEW FIELDS ############

    public String getExamQuestions() {
        return examQuestions;
    }

    public void setExamQuestions(String examQuestions) {
        this.examQuestions = examQuestions;
    }

    public String getExamAnswers() {
        return examAnswers;
    }

    public void setExamAnswers(String examAnswers) {
        this.examAnswers = examAnswers;
    }

    public Boolean getExamSubmitted() {
        return examSubmitted;
    }

    public void setExamSubmitted(Boolean examSubmitted) {
        this.examSubmitted = examSubmitted;
    }

    public Integer getExamScore() {
        return examScore;
    }

    public void setExamScore(Integer examScore) {
        this.examScore = examScore;
    }

    public LocalDate getOfferStartDate() {
        return offerStartDate;
    }

    public void setOfferStartDate(LocalDate offerStartDate) {
        this.offerStartDate = offerStartDate;
    }

    public String getOfferStartTime() {
        return offerStartTime;
    }

    public void setOfferStartTime(String offerStartTime) {
        this.offerStartTime = offerStartTime;
    }

    public String getOfferLocation() {
        return offerLocation;
    }

    public void setOfferLocation(String offerLocation) {
        this.offerLocation = offerLocation;
    }

    public String getOfferRequiredPapers() {
        return offerRequiredPapers;
    }

    public void setOfferRequiredPapers(String offerRequiredPapers) {
        this.offerRequiredPapers = offerRequiredPapers;
    }
}