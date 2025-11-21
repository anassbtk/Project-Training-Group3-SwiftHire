package com.example.jobrecruitmentsystem.controller;

import com.example.jobrecruitmentsystem.model.*;
import com.example.jobrecruitmentsystem.repository.JobApplicationRepository;
import com.example.jobrecruitmentsystem.repository.JobRequirementRepository;
import com.example.jobrecruitmentsystem.repository.SeekerProfileRepository;
import com.example.jobrecruitmentsystem.service.FileService;
import com.example.jobrecruitmentsystem.service.UserService;
import com.example.jobrecruitmentsystem.service.AiAssistantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.hibernate.Hibernate;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.format.DateTimeFormatter;
import java.net.URI;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

@Controller
@RequestMapping("/employer")
public class EmployerController {

    // --- Internal classes for JSON parsing ---
    private static class WorkExperience {
        public Long id;
        public String jobTitle;
        public String companyName;
        public String description;
        public String startDate;
        public String endDate;
    }

    private static class Education {
        public Long id;
        public String institutionName;
        public String degree;
        public String fieldOfStudy;
        public Integer startYear;
        public Integer endYear;
    }
    // -----------------------------------------

    private final UserService userService;
    private final JobRequirementRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SeekerProfileRepository seekerProfileRepository;
    private final AiAssistantService aiAssistantService;

    // Constants
    private final List<String> JOB_TYPES = AppConstants.ALL_JOB_TYPES;
    private final List<String> JOB_CATEGORIES = AppConstants.ALL_JOB_CATEGORIES;
    private final List<String> APPLICATION_STATUSES = List.of("APPLIED", "REVIEWED", "ACCEPTED", "REJECTED", "HIRED");

    public EmployerController(UserService userService,
                              JobRequirementRepository jobRepository,
                              JobApplicationRepository applicationRepository,
                              FileService fileService,
                              SeekerProfileRepository seekerProfileRepository,
                              AiAssistantService aiAssistantService) {
        this.userService = userService;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.fileService = fileService;
        this.seekerProfileRepository = seekerProfileRepository;
        this.aiAssistantService = aiAssistantService;
    }

    @ModelAttribute
    public void populateSidebarData(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return;

        User currentUser = userService.findByUsername(userDetails.getUsername());
        if (currentUser != null) {
            model.addAttribute("user", currentUser);
            model.addAttribute("companyName", currentUser.getCompanyName());
            model.addAttribute("companyLogoFilename", currentUser.getCompanyLogoFilename());

            String tier = currentUser.getPremiumTier();
            boolean isPremiumOrPro = tier != null &&
                    (tier.equalsIgnoreCase(AppConstants.TIER_PREMIUM) || tier.equalsIgnoreCase(AppConstants.TIER_PRO));

            model.addAttribute("isPremium", isPremiumOrPro);

            long activeCount = jobRepository.countByPostedBy_IdAndStatusIn(currentUser.getId(), List.of("ACTIVE"));
            long pendingCount = jobRepository.countByPostedBy_IdAndStatusIn(currentUser.getId(), List.of("PENDING_ADMIN"));
            long onHoldCount = jobRepository.countByPostedBy_IdAndStatusIn(currentUser.getId(), List.of("ON_HOLD"));

            model.addAttribute("activeJobCount", activeCount);
            model.addAttribute("pendingJobCount", pendingCount);
            model.addAttribute("onHoldJobCount", onHoldCount);
            model.addAttribute("totalJobCount", activeCount + pendingCount + onHoldCount);
        }
    }

    // --- NEW: AI CANDIDATE ANALYZER ENDPOINT ---
    @PostMapping("/api/ai/analyze-candidate")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> analyzeCandidateProfile(@RequestBody Map<String, String> payload,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        String tier = user.getPremiumTier();
        boolean isPremium = tier != null && (tier.equalsIgnoreCase(AppConstants.TIER_PREMIUM) || tier.equalsIgnoreCase(AppConstants.TIER_PRO));

        if (!isPremium) {
            return ResponseEntity.status(403).body(Map.of("error", "Premium required."));
        }

        String profileText = payload.get("profileText");
        if (profileText == null || profileText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No profile data provided."));
        }

        Map<String, Object> analysis = aiAssistantService.generateCandidateAnalysis(profileText);
        return ResponseEntity.ok(analysis);
    }

    // --- AI JOB DESCRIPTION GENERATOR ---
    @PostMapping("/api/ai/generate-job-desc")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> generateJobDescription(@RequestBody Map<String, String> payload,
                                                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        String tier = user.getPremiumTier();

        boolean isPremium = tier != null && (tier.equalsIgnoreCase(AppConstants.TIER_PREMIUM) || tier.equalsIgnoreCase(AppConstants.TIER_PRO));

        if (!isPremium) {
            return ResponseEntity.status(403).body(Map.of("error", "Upgrade to Premium to use AI generation."));
        }

        String title = payload.get("title");
        String location = payload.get("location");
        String jobType = payload.get("jobType");
        String salaryMin = payload.get("salaryMin");
        String salaryMax = payload.get("salaryMax");

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Job title is required."));
        }

        String salaryRange = "";
        if (salaryMin != null && !salaryMin.isBlank()) {
            salaryRange = "$" + salaryMin;
            if (salaryMax != null && !salaryMax.isBlank()) {
                salaryRange += " - $" + salaryMax;
            }
        }

        try {
            String description = aiAssistantService.generateJobDescription(title, location, jobType, salaryRange);
            return ResponseEntity.ok(Map.of("description", description));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "AI Service Error: " + e.getMessage()));
        }
    }

    // ############ METHOD: RELEASE HELD JOBS ############
    @PostMapping("/jobs/release-held")
    @Transactional
    public String releaseHeldJobs(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        User user = userService.findByUsername(userDetails.getUsername());
        String tier = user.getPremiumTier();
        boolean isPremium = tier != null && (tier.equalsIgnoreCase(AppConstants.TIER_PREMIUM) || tier.equalsIgnoreCase(AppConstants.TIER_PRO));

        if (!isPremium) {
            redirectAttributes.addFlashAttribute("errorMessage", "You must be Premium to release held jobs.");
            return "redirect:/employer/dashboard";
        }

        List<JobRequirement> allHeldJobs = jobRepository.findByStatus("ON_HOLD");
        int count = 0;
        for (JobRequirement job : allHeldJobs) {
            if (job.getPostedBy().getId().equals(user.getId())) {
                job.setStatus("PENDING_ADMIN");
                job.setIsFeatured(true);
                jobRepository.save(job);
                count++;
            }
        }
        if (count > 0) {
            redirectAttributes.addFlashAttribute("successMessage", "Successfully released " + count + " jobs!");
        }
        return "redirect:/employer/dashboard";
    }

    // ############ DASHBOARD ############
    @GetMapping({"/onboarding/company-setup", "/dashboard"})
    @Transactional(readOnly = true)
    public String employerDashboard(Model model,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam(value = "status", required = false) String statusFilter,
                                    @RequestParam(value = "view", required = false) String view,
                                    @RequestParam(value = "searchSkills", required = false) String searchSkills,
                                    @RequestParam(value = "searchCity", required = false) String searchCity,
                                    @RequestParam(value = "searchTitle", required = false) String searchTitle) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        String companyName = currentUser.getCompanyName();

        String requestURI = (String) model.asMap().get("requestURI");
        if (requestURI != null && requestURI.endsWith("/onboarding/company-setup") && companyName != null && !companyName.isBlank()) {
            return "redirect:/employer/dashboard";
        }

        if (companyName == null || companyName.isBlank()) {
            model.addAttribute("errorMessage", "Please complete your company profile setup.");
        }

        boolean isPremiumOrPro = (Boolean) model.getAttribute("isPremium");

        List<JobApplication> allApplications = applicationRepository.findAll().stream()
                .filter(app -> {
                    try {
                        if (app.getJob() == null) return false;
                        if (app.getJob().getPostedBy() == null) return false;
                        return companyName != null && companyName.equals(app.getJob().getPostedBy().getCompanyName());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        List<JobRequirement> allJobsForCounts = jobRepository.findAll().stream()
                .filter(job -> job.getPostedBy() != null && job.getPostedBy().getCompanyName() != null && job.getPostedBy().getCompanyName().equals(companyName))
                .collect(Collectors.toList());

        List<JobApplication> inboxApplications = allApplications.stream()
                .sorted(Comparator.comparing(JobApplication::getAppliedAt).reversed())
                .collect(Collectors.toList());

        inboxApplications.forEach(app -> Hibernate.initialize(app.getSeeker()));
        model.addAttribute("inboxApplications", inboxApplications);

        List<JobRequirement> postedJobs;
        if (statusFilter == null || statusFilter.equalsIgnoreCase("ALL")) {
            postedJobs = allJobsForCounts;
        } else {
            postedJobs = allJobsForCounts.stream()
                    .filter(job -> job.getStatus().equalsIgnoreCase(statusFilter))
                    .collect(Collectors.toList());
        }
        model.addAttribute("jobs", postedJobs);

        Map<String, Long> dashboardStats = allApplications.stream()
                .collect(Collectors.groupingBy(JobApplication::getStatus, Collectors.counting()));

        for (String s : APPLICATION_STATUSES) {
            dashboardStats.putIfAbsent(s, 0L);
        }
        Long oldInterviewCount = dashboardStats.getOrDefault("INTERVIEW", 0L);
        if (oldInterviewCount > 0) {
            dashboardStats.merge("ACCEPTED", oldInterviewCount, Long::sum);
            dashboardStats.remove("INTERVIEW");
        }
        dashboardStats.put("TOTAL_APPLICATIONS", (long) allApplications.size());

        Map<Long, Long> applicantCountsPerJob = allApplications.stream()
                .collect(Collectors.groupingBy(app -> app.getJob().getId(), Collectors.counting()));

        List<SeekerProfile> candidates;

        if ("candidates".equals(view)) {
            if (isPremiumOrPro) {
                // UPDATED: Using searchTitle instead of searchCategory
                String sSkills = (searchSkills != null && !searchSkills.isBlank()) ? searchSkills.trim() : null;
                String sCity = (searchCity != null && !searchCity.isBlank()) ? searchCity.trim() : null;
                String sTitle = (searchTitle != null && !searchTitle.isBlank()) ? searchTitle.trim() : null;
                candidates = seekerProfileRepository.findFilteredCandidates(sSkills, sCity, sTitle);
            } else {
                candidates = seekerProfileRepository.findFilteredCandidates(null, null, null);
                if (searchSkills != null || searchCity != null || searchTitle != null) {
                    model.addAttribute("errorMessage", "Advanced candidate search filters require a Premium subscription.");
                }
            }
            model.addAttribute("searchSkills", searchSkills);
            model.addAttribute("searchCity", searchCity);
            model.addAttribute("searchTitle", searchTitle);
        } else {
            candidates = seekerProfileRepository.findFilteredCandidates(null, null, null);
        }

        candidates.forEach(profile -> Hibernate.initialize(profile.getUser()));
        model.addAttribute("candidates", candidates);
        model.addAttribute("candidateCount", candidates.size());
        model.addAttribute("allCategories", AppConstants.ALL_JOB_CATEGORIES);

        model.addAttribute("currentView", view != null ? view : "overview");
        model.addAttribute("currentFilter", statusFilter != null ? statusFilter.toUpperCase() : "ALL");
        model.addAttribute("companyDescription", currentUser.getCompanyDescription());
        model.addAttribute("companyLocation", currentUser.getCompanyLocation());
        model.addAttribute("dashboardStats", dashboardStats);
        model.addAttribute("applicantCountsPerJob", applicantCountsPerJob);

        return "employer/dashboard";
    }

    @GetMapping("/candidates")
    @Transactional(readOnly = true)
    public String findCandidates(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        return "redirect:/employer/dashboard?view=candidates";
    }

    @GetMapping("/messages")
    public String redirectToInbox() {
        return "redirect:/employer/dashboard?view=messages";
    }

    @PostMapping("/candidate/contact/{seekerId}")
    @Transactional
    public String contactCandidate(@PathVariable Long seekerId,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        User currentEmployer = userService.findByUsername(userDetails.getUsername());
        if (currentEmployer == null || currentEmployer.getCompanyName() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Company profile required.");
            return "redirect:/employer/dashboard";
        }

        String tier = currentEmployer.getPremiumTier();
        boolean isPremium = tier != null && (tier.equalsIgnoreCase(AppConstants.TIER_PREMIUM) || tier.equalsIgnoreCase(AppConstants.TIER_PRO));

        if (!isPremium) {
            redirectAttributes.addFlashAttribute("errorMessage", "Direct contact is a Premium feature.");
            return "redirect:/employer/dashboard?view=candidates";
        }

        User candidate = userService.findById(seekerId);
        if (candidate == null || !"JOB_SEEKER".equals(candidate.getRole().getName())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Candidate not found.");
            return "redirect:/employer/dashboard?view=candidates";
        }

        List<JobApplication> existingApps = applicationRepository.findAll().stream()
                .filter(app -> app.getSeeker().getId().equals(seekerId) &&
                        app.getJob().getPostedBy().getId().equals(currentEmployer.getId()))
                .collect(Collectors.toList());

        if (!existingApps.isEmpty()) {
            JobApplication latest = existingApps.get(0);
            return "redirect:/employer/dashboard?view=messages&appId=" + latest.getId();
        }

        List<JobRequirement> myJobs = jobRepository.findAll().stream()
                .filter(j -> j.getPostedBy().getId().equals(currentEmployer.getId()) && "ACTIVE".equals(j.getStatus()))
                .collect(Collectors.toList());

        if (myJobs.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You need at least one Active job post to initiate contact.");
            return "redirect:/employer/dashboard?view=candidates";
        }

        JobRequirement jobToUse = myJobs.get(0);

        JobApplication newApp = new JobApplication(candidate, jobToUse);
        newApp.setStatus("REVIEWED");
        newApp.setAppliedAt(LocalDateTime.now());

        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("senderId", currentEmployer.getId());
            msg.put("senderFirstName", currentEmployer.getCompanyName());
            msg.put("senderLastName", "(Direct)");
            msg.put("message", "Hi " + candidate.getFirstName() + ", we viewed your profile and would like to discuss an opportunity.");
            msg.put("createdAt", LocalDate.now().toString());
            msg.put("senderRole", "EMPLOYER");

            List<Map<String, Object>> log = new ArrayList<>();
            log.add(msg);
            newApp.setMessageLog(objectMapper.writeValueAsString(log));
        } catch (Exception e) {
            newApp.setMessageLog("[]");
        }

        applicationRepository.save(newApp);

        redirectAttributes.addFlashAttribute("successMessage", "Conversation started with " + candidate.getFirstName());
        return "redirect:/employer/dashboard?view=messages&appId=" + newApp.getId();
    }

    @GetMapping("/logos/download/{filename}")
    public ResponseEntity<Resource> downloadCompanyLogo(@PathVariable String filename) {
        try {
            Resource resource = fileService.loadFileAsResource(FileService.COMPANY_LOGO_SUBDIR, filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/resumes/download/{filename}")
    public ResponseEntity<Resource> downloadSeekerResume(@PathVariable String filename, @AuthenticationPrincipal UserDetails userDetails) {
        User employer = userService.findByUsername(userDetails.getUsername());
        if (employer.getCompanyName() == null) {
            return ResponseEntity.status(403).build();
        }
        try {
            Resource resource = fileService.loadFileAsResource(FileService.RESUME_SUBDIR, filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/jobs/new")
    public String showNewJobForm(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("job", new JobRequirement());
        model.addAttribute("jobTypes", JOB_TYPES);
        model.addAttribute("jobCategories", JOB_CATEGORIES);
        return "employer/new_job";
    }

    @PostMapping("/jobs/new")
    @Transactional
    public String handleNewJobSubmission(@ModelAttribute("job") JobRequirement job,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         @RequestParam(value = "logoFile", required = false) MultipartFile logoFile,
                                         RedirectAttributes redirectAttributes) {

        User employer = userService.findByUsername(userDetails.getUsername());
        String companyName = employer.getCompanyName();

        if (companyName == null || companyName.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot post job: Your account is not associated with a company.");
            return "redirect:/employer/dashboard";
        }

        if (logoFile != null && !logoFile.isEmpty()) {
            try {
                String newLogoFilename = fileService.saveCompanyLogoFile(logoFile);
                employer.setCompanyLogoFilename(newLogoFilename);
                userService.saveUser(employer);
            } catch (IOException e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Error uploading logo: " + e.getMessage());
                return "redirect:/employer/jobs/new";
            }
        }

        if (job.getJobCategory() != null && job.getJobCategory().trim().isEmpty()) {
            job.setJobCategory(null);
        }

        String tier = employer.getPremiumTier();
        boolean isPremiumOrPro = tier != null && (tier.equalsIgnoreCase(AppConstants.TIER_PREMIUM) || tier.equalsIgnoreCase(AppConstants.TIER_PRO));

        String statusToSet = "PENDING_ADMIN";
        String messageToUser = "Job posted successfully and awaiting admin approval!";

        if (isPremiumOrPro) {
            job.setIsFeatured(true);
        } else {
            job.setIsFeatured(false);
        }

        if (!isPremiumOrPro) {
            long currentJobCount = jobRepository.countByPostedBy_IdAndStatusIn(
                    employer.getId(), List.of("ACTIVE", "PENDING_ADMIN")
            );

            if (currentJobCount >= 5) {
                statusToSet = "ON_HOLD";
                messageToUser = "Job saved but placed ON HOLD. You have reached the 5-job limit for Basic accounts. Upgrade to Premium to activate this job.";
            }
        }

        job.setPostedBy(employer);
        job.setStatus(statusToSet);
        jobRepository.save(job);

        redirectAttributes.addFlashAttribute(
                "ON_HOLD".equals(statusToSet) ? "errorMessage" : "successMessage",
                messageToUser
        );

        return "redirect:/employer/dashboard";
    }

    @GetMapping("/company/edit")
    @Transactional(readOnly = true)
    public String showCompanyProfileEditForm(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        if (currentUser.getCompanyName() == null) {
            model.addAttribute("errorMessage", "You are not associated with a company. Please contact support.");
            return "redirect:/employer/dashboard";
        }

        Map<String, Object> companyForm = new HashMap<>();
        companyForm.put("name", currentUser.getCompanyName());
        companyForm.put("description", currentUser.getCompanyDescription());
        companyForm.put("location", currentUser.getCompanyLocation());
        companyForm.put("logoFilename", currentUser.getCompanyLogoFilename());
        model.addAttribute("company", companyForm);

        return "employer/company_edit";
    }

    @PostMapping("/company/edit")
    @Transactional
    public String handleCompanyProfileUpdate(
            @RequestParam("name") String name,
            @RequestParam("location") String location,
            @RequestParam("description") String description,
            @RequestParam("logoFile") MultipartFile logoFile,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        if (currentUser.getCompanyName() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized operation.");
            return "redirect:/employer/dashboard";
        }
        try {
            currentUser.setCompanyName(name);
            currentUser.setCompanyDescription(description);
            currentUser.setCompanyLocation(location);
            if (logoFile != null && !logoFile.isEmpty()) {
                String newLogoFilename = fileService.saveCompanyLogoFile(logoFile);
                currentUser.setCompanyLogoFilename(newLogoFilename);
            }
            userService.saveUser(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Company profile updated successfully!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating profile: " + e.getMessage());
        }
        return "redirect:/employer/company/edit";
    }

    @GetMapping("/jobs/{jobId}/applicants")
    @Transactional(readOnly = true)
    public String viewApplicants(@PathVariable Long jobId,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 Model model) {
        JobRequirement job = jobRepository.findById(jobId).orElse(null);
        User currentEmployer = userService.findByUsername(userDetails.getUsername());
        String employersCompanyName = currentEmployer.getCompanyName();

        if (job == null || job.getPostedBy() == null || employersCompanyName == null || !employersCompanyName.equals(job.getPostedBy().getCompanyName())) {
            model.addAttribute("errorMessage", "Job not found or access denied.");
            return "redirect:/employer/dashboard";
        }

        List<JobApplication> applications = applicationRepository.findByJob(job);

        applications.forEach(app -> {
            if (app.getSeeker() != null) {
                Hibernate.initialize(app.getSeeker().getSeekerProfile());
            }
        });

        model.addAttribute("job", job);
        model.addAttribute("applications", applications);
        model.addAttribute("applicationStatuses", APPLICATION_STATUSES);
        return "employer/applicants";
    }

    // ############ NEW: SET EXAM QUESTIONS ############
    @PostMapping("/applications/{appId}/exam/set")
    @Transactional
    public String setApplicationExam(@PathVariable Long appId,
                                     @RequestParam("questions") String questions,
                                     @RequestParam("examAnswers") String examAnswers,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {

        JobApplication application = applicationRepository.findApplicationWithDetailsById(appId).orElse(null);
        User currentEmployer = userService.findByUsername(userDetails.getUsername());

        if (application == null || !currentEmployer.getCompanyName().equals(application.getJob().getPostedBy().getCompanyName())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found or unauthorized.");
            return "redirect:/employer/dashboard";
        }

        if (questions == null || questions.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Exam questions cannot be empty.");
            return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
        }

        // Update application fields
        application.setExamQuestions(questions);
        application.setExamAnswers(examAnswers);
        application.setExamSubmitted(false);
        application.setExamScore(0); // Reset score

        applicationRepository.saveAndFlush(application);

        redirectAttributes.addFlashAttribute("successMessage", "Exam questions assigned to " + application.getSeeker().getFirstName() + ".");
        return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
    }

    // ############ NEW: SET FINAL JOB OFFER ############
    @PostMapping("/applications/{appId}/offer/set")
    @Transactional
    public String setJobOffer(@PathVariable Long appId,
                              @RequestParam("offerStartDate") String offerStartDate,
                              @RequestParam("offerStartTime") String offerStartTime,
                              @RequestParam("offerLocation") String offerLocation,
                              @RequestParam("offerRequiredPapers") String offerRequiredPapers,
                              @AuthenticationPrincipal UserDetails userDetails,
                              RedirectAttributes redirectAttributes) {

        JobApplication application = applicationRepository.findApplicationWithDetailsById(appId).orElse(null);
        User currentEmployer = userService.findByUsername(userDetails.getUsername());

        if (application == null || !currentEmployer.getCompanyName().equals(application.getJob().getPostedBy().getCompanyName())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found or unauthorized.");
            return "redirect:/employer/dashboard";
        }

        if (!application.getStatus().equals("ACCEPTED")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Offer can only be set for an 'ACCEPTED' application.");
            return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
        }

        // Convert date string to LocalDate
        LocalDate startDate = LocalDate.parse(offerStartDate, DateTimeFormatter.ISO_DATE);

        application.setOfferStartDate(startDate);
        application.setOfferStartTime(offerStartTime);
        application.setOfferLocation(offerLocation);
        application.setOfferRequiredPapers(offerRequiredPapers);

        application.setStatus("HIRED"); // Move to final status

        applicationRepository.saveAndFlush(application);

        redirectAttributes.addFlashAttribute("successMessage", "Final Job Offer sent to " + application.getSeeker().getFirstName() + "!");
        return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
    }

    // ############ NEW: SET EXAM SCORE ############
    @PostMapping("/candidate/{userId}/score")
    @Transactional
    public String setExamScore(@PathVariable Long userId,
                               @RequestParam("applicationId") Long applicationId,
                               @RequestParam("examScore") Integer examScore,
                               RedirectAttributes redirectAttributes) {

        JobApplication application = applicationRepository.findById(applicationId).orElse(null);

        if (application == null || !application.getSeeker().getId().equals(userId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found or invalid.");
            return "redirect:/employer/dashboard";
        }

        // Defensive check: Ensure we can get the jobId before using it in the redirect
        if (application.getJob() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application job link is missing.");
            return "redirect:/employer/dashboard";
        }

        if (!application.getExamSubmitted()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot score unsubmitted exam.");
            return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
        }

        application.setExamScore(examScore);
        applicationRepository.saveAndFlush(application);

        redirectAttributes.addFlashAttribute("successMessage", "Exam score set to " + examScore + " for " + application.getSeeker().getFirstName() + ".");

        // FIX: Redirect back to the job's applicants list page
        Long jobId = application.getJob().getId();
        return "redirect:/employer/jobs/" + jobId + "/applicants";
        // END FIX
    }

    @PostMapping("/applications/{applicationId}/status")
    @Transactional
    public String updateApplicationStatus(@PathVariable Long applicationId,
                                          @RequestParam("newStatus") String newStatus,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          RedirectAttributes redirectAttributes) {

        JobApplication application = applicationRepository.findById(applicationId).orElse(null);
        User currentEmployer = userService.findByUsername(userDetails.getUsername());

        if (application == null || application.getJob() == null || application.getJob().getPostedBy() == null ||
                currentEmployer.getCompanyName() == null || !currentEmployer.getCompanyName().equals(application.getJob().getPostedBy().getCompanyName())) {

            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized action.");
            return "redirect:/employer/dashboard";
        }

        // --- NEW LOGIC: VALIDATION FOR STATUS CHANGES ---
        if ("ACCEPTED".equals(newStatus) && application.getExamQuestions() != null && !application.getExamSubmitted()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot accept: The applicant has not yet submitted the required exam.");
            return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
        }
        if ("HIRED".equals(newStatus) && application.getOfferStartDate() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot set status to HIRED manually without sending a final Job Offer first.");
            return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
        }
        // --- END NEW LOGIC ---

        application.setStatus(newStatus);

        String autoReplyMessage = null;
        switch (newStatus) {
            case "REVIEWED":
                autoReplyMessage = "We have reviewed your application for the " + application.getJob().getTitle() + " position and are currently considering it. We will let you know about the next steps.";
                break;
            case "ACCEPTED":
                autoReplyMessage = "Congratulations! We were impressed with your application and would like to move to the next step. You will receive an official email shortly detailing the next phase, which will include required documents and location details.";
                break;
            case "REJECTED":
                autoReplyMessage = "Thank you for your interest in the " + application.getJob().getTitle() + " position. After careful consideration, we have decided to move forward with other candidates. We wish you the best in your job search.";
                break;
            case "HIRED":
                // Offer logic handles setting HIRED, manual switch is blocked above.
                autoReplyMessage = "Congratulations, you are hired for the " + application.getJob().getTitle() + " position! We are excited to welcome you to " + currentEmployer.getCompanyName() + ". An official hiring package will be sent to your email address shortly.";
                break;
            default:
                autoReplyMessage = null;
        }

        if (autoReplyMessage != null) {
            try {
                String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
                List<Map<String, Object>> notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});

                Map<String, Object> newNote = new HashMap<>();
                newNote.put("senderId", currentEmployer.getId());
                newNote.put("senderFirstName", currentEmployer.getCompanyName());
                newNote.put("senderLastName", "Hiring Team");
                newNote.put("message", autoReplyMessage);
                newNote.put("createdAt", LocalDate.now().toString());
                newNote.put("senderRole", "EMPLOYER");

                notes.add(newNote);
                application.setMessageLog(objectMapper.writeValueAsString(notes));

            } catch (IOException e) {
                System.err.println("Failed to add auto-reply message: " + e.getMessage());
            }
        }

        applicationRepository.saveAndFlush(application);

        redirectAttributes.addFlashAttribute("successMessage", "Application status updated to " + newStatus);
        return "redirect:/employer/jobs/" + application.getJob().getId() + "/applicants";
    }

    // In EmployerController.java, update the viewApplicationDialogue method around line 680

    @GetMapping("/applications/{appId}")
    @Transactional(readOnly = true)
    public String viewApplicationDialogue(@PathVariable Long appId,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          Model model,
                                          RedirectAttributes redirectAttributes) {

        User currentEmployer = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findApplicationWithDetailsById(appId);

        if (appOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found or its associated job has been deleted.");
            return "redirect:/employer/dashboard?view=messages";
        }

        JobApplication application = appOpt.get();

        // Handle case where job has been deleted
        if (application.getJob() == null) {
            model.addAttribute("jobDeleted", true);
        } else if (currentEmployer.getCompanyName() == null || !currentEmployer.getCompanyName().equals(application.getJob().getPostedBy().getCompanyName())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access Denied.");
            return "redirect:/employer/dashboard?view=messages";
        }

        List<Map<String, Object>> notes = new ArrayList<>();
        try {
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});

            // Clean up messages that reference deleted job
            if (application.getJob() == null) {
                for (Map<String, Object> note : notes) {
                    String message = (String) note.get("message");
                    if (message != null) {
                        // Replace placeholder patterns
                        message = message.replaceAll("\\*\\*[^*]+\\*\\*", "[Job Details Unavailable]");
                        message = message.replaceAll("for the \"[^\"]*\" position", "for this position");
                        note.put("message", message);
                    }
                }
            }
        } catch (IOException e) {
        }

        model.addAttribute("application", application);
        model.addAttribute("notes", notes);
        model.addAttribute("user", currentEmployer);
        model.addAttribute("isSeeker", false);
        model.addAttribute("currentView", "messages");

        return "seeker/application_action_guide.html";
    }

    @PostMapping("/applications/note/add")
    @Transactional
    public String addApplicationNote(@RequestParam("applicationId") Long applicationId,
                                     @RequestParam("message") String message,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {

        User currentEmployer = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findApplicationWithDetailsById(applicationId);

        if (appOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found.");
            return "redirect:/employer/dashboard";
        }

        JobApplication application = appOpt.get();

        if (currentEmployer.getCompanyName() == null || !currentEmployer.getCompanyName().equals(application.getJob().getPostedBy().getCompanyName())) {
            return "redirect:/employer/dashboard";
        }

        if (message == null || message.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Message cannot be empty.");
            return "redirect:/employer/applications/" + applicationId;
        }

        try {
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentEmployer.getId());
            newNote.put("senderFirstName", currentEmployer.getFirstName());
            newNote.put("senderLastName", currentEmployer.getLastName());
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "EMPLOYER");

            notes.add(newNote);
            application.setMessageLog(objectMapper.writeValueAsString(notes));

            applicationRepository.saveAndFlush(application);

            redirectAttributes.addFlashAttribute("successMessage", "Message sent to applicant!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to send message due to data error.");
        }

        return "redirect:/employer/applications/" + applicationId;
    }

    @GetMapping("/api/support/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getSupportMessages(@AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        String chatLogJson = currentUser.getAdminSupportChatLog() != null ? currentUser.getAdminSupportChatLog() : "[]";

        try {
            List<Map<String, Object>> messages = objectMapper.readValue(chatLogJson, new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Object> permanentWelcome = new HashMap<>();
            permanentWelcome.put("senderId", 1L);
            permanentWelcome.put("senderFirstName", "Global");
            permanentWelcome.put("senderLastName", "Admin");
            permanentWelcome.put("message", "Welcome to the Employer Support Chat. We're here to assist with any platform issues, job posting queries, or account management questions. Please submit your request below.");
            permanentWelcome.put("createdAt", LocalDate.now().toString());
            permanentWelcome.put("senderRole", "ADMIN");

            List<Map<String, Object>> finalMessages = new ArrayList<>();
            finalMessages.add(permanentWelcome);
            finalMessages.addAll(messages);
            return ResponseEntity.ok(finalMessages);
        } catch (IOException e) {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @PostMapping("/api/support/note/add")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> addSupportNote(@RequestBody Map<String, String> payload,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        String message = payload.get("message");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Message cannot be empty.");
        }

        try {
            String chatLogJson = currentUser.getAdminSupportChatLog() != null ? currentUser.getAdminSupportChatLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(chatLogJson, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentUser.getId());
            newNote.put("senderFirstName", currentUser.getFirstName());
            newNote.put("senderLastName", currentUser.getLastName());
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "EMPLOYER");
            notes.add(newNote);

            currentUser.setAdminSupportChatLog(objectMapper.writeValueAsString(notes));
            userService.saveUser(currentUser);

            return ResponseEntity.ok("{\"status\": \"success\"}");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"status\": \"error\"}");
        }
    }

    @GetMapping("/api/applications/{appId}/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<String> getMessagesJson(@PathVariable Long appId, @AuthenticationPrincipal UserDetails userDetails) {
        User currentEmployer = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findById(appId);
        if (appOpt.isEmpty()) return ResponseEntity.notFound().build();
        JobApplication application = appOpt.get();

        if (application.getJob() == null || !currentEmployer.getCompanyName().equals(application.getJob().getPostedBy().getCompanyName())) {
            return ResponseEntity.status(403).body("Access Denied");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(application.getMessageLog() != null ? application.getMessageLog() : "[]");
    }

    @PostMapping("/api/applications/{appId}/messages")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> postMessageJson(@PathVariable Long appId, @RequestBody Map<String, String> payload, @AuthenticationPrincipal UserDetails userDetails) {
        String message = payload.get("message");
        if (message == null || message.isBlank()) return ResponseEntity.badRequest().body("Message cannot be empty.");
        User currentEmployer = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findById(appId);
        if (appOpt.isEmpty()) return ResponseEntity.notFound().build();
        JobApplication application = appOpt.get();

        if (application.getJob() == null || !currentEmployer.getCompanyName().equals(application.getJob().getPostedBy().getCompanyName())) {
            return ResponseEntity.status(403).body("Access Denied.");
        }

        try {
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentEmployer.getId());
            newNote.put("senderFirstName", currentEmployer.getFirstName());
            newNote.put("senderLastName", currentEmployer.getLastName());
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "EMPLOYER");
            notes.add(newNote);
            application.setMessageLog(objectMapper.writeValueAsString(notes));
            applicationRepository.saveAndFlush(application);
            return ResponseEntity.ok().body("{\"status\": \"success\"}");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"status\": \"error\"}");
        }
    }

    @GetMapping("/candidate/{userId}")
    @Transactional(readOnly = true)
    public String viewCandidateProfile(@PathVariable Long userId, Model model, @AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        User currentEmployer = userService.findByUsername(userDetails.getUsername());
        if (currentEmployer.getCompanyName() == null || currentEmployer.getCompanyName().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You must be an employer to view profiles.");
            return "redirect:/employer/dashboard";
        }

        User candidateUser = userService.findById(userId);

        if (candidateUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Candidate not found.");
            return "redirect:/employer/dashboard?view=candidates";
        }

        SeekerProfile profile = seekerProfileRepository.findByUser(candidateUser).orElse(null);

        if (profile == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Candidate profile not found.");
            return "redirect:/employer/dashboard?view=candidates";
        }

        try {
            String workJson = profile.getWorkExperienceJson() != null ? profile.getWorkExperienceJson() : "[]";
            List<WorkExperience> workExperiences = objectMapper.readValue(workJson, new TypeReference<List<WorkExperience>>() {});
            model.addAttribute("workExperiences", workExperiences);

            String eduJson = profile.getEducationJson() != null ? profile.getEducationJson() : "[]";
            List<Education> educationHistory = objectMapper.readValue(eduJson, new TypeReference<List<Education>>() {});
            model.addAttribute("educationHistory", educationHistory);

        } catch (IOException e) {
            model.addAttribute("workExperiences", new ArrayList<>());
            model.addAttribute("educationHistory", new ArrayList<>());
            model.addAttribute("errorMessage", "Error parsing profile history.");
        }

        model.addAttribute("profile", profile);
        model.addAttribute("user", candidateUser);
        model.addAttribute("currentView", "candidates");

        // --- PREMIUM CHECK: Pass to view to toggle AI buttons ---
        String tier = currentEmployer.getPremiumTier();
        boolean isPremium = tier != null && (tier.equalsIgnoreCase(AppConstants.TIER_PREMIUM) || tier.equalsIgnoreCase(AppConstants.TIER_PRO));
        model.addAttribute("isPremium", isPremium);

        return "employer/view_candidate_profile";
    }
}