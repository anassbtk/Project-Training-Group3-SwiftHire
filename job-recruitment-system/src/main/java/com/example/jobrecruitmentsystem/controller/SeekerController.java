package com.example.jobrecruitmentsystem.controller;

import com.example.jobrecruitmentsystem.model.*;
import com.example.jobrecruitmentsystem.repository.JobApplicationRepository;
import com.example.jobrecruitmentsystem.repository.SeekerProfileRepository;
import com.example.jobrecruitmentsystem.repository.JobRequirementRepository;
import com.example.jobrecruitmentsystem.service.FileService;
import com.example.jobrecruitmentsystem.service.UserService;
import com.example.jobrecruitmentsystem.service.AiAssistantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.Set;

@Controller
@RequestMapping("/seeker")
public class SeekerController {

    // --- INTERNAL DATA CLASSES ---
    public static class WorkExperience {
        public Long id = System.currentTimeMillis();
        public String jobTitle;
        public String companyName;
        public String description;
        public String startDate;
        public String endDate;
    }

    public static class Education {
        public Long id = System.currentTimeMillis();
        public String institutionName;
        public String degree;
        public String fieldOfStudy;
        public Integer startYear;
        public Integer endYear;
    }
    // --- END INTERNAL DATA CLASSES ---

    private final UserService userService;
    private final SeekerProfileRepository profileRepository;
    private final JobApplicationRepository applicationRepository;
    private final FileService fileService;
    private final JobRequirementRepository jobRepository;
    private final AiAssistantService aiAssistantService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- APPLICATION CONSTANTS ---
    private static final int MAX_BASIC_APPLICATIONS = 10;

    public SeekerController(UserService userService,
                            SeekerProfileRepository profileRepository,
                            JobApplicationRepository applicationRepository,
                            FileService fileService,
                            JobRequirementRepository jobRepository,
                            AiAssistantService aiAssistantService) {
        this.userService = userService;
        this.profileRepository = profileRepository;
        this.applicationRepository = applicationRepository;
        this.fileService = fileService;
        this.jobRepository = jobRepository;
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public String seekerDashboard(@AuthenticationPrincipal UserDetails userDetails,
                                  Model model,
                                  @RequestParam(value = "view", required = false) String view,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "jobType", required = false) List<String> jobTypes,
                                  @RequestParam(value = "location", required = false) String location,
                                  @RequestParam(value = "category", required = false) String category,
                                  @RequestParam(value = "recommended", required = false) String recommended) {

        User currentSeeker = userService.findByUsername(userDetails.getUsername());
        Optional<SeekerProfile> profileOpt = profileRepository.findByUser(currentSeeker);
        SeekerProfile profile = profileOpt.orElse(null);

        // --- PREMIUM CHECKS & COUNTS ---
        boolean isPremiumOrPro = currentSeeker.getPremiumTier() != null &&
                (currentSeeker.getPremiumTier().equals(AppConstants.TIER_PREMIUM) ||
                        currentSeeker.getPremiumTier().equals(AppConstants.TIER_PRO));

        model.addAttribute("isPremium", isPremiumOrPro);
        model.addAttribute("applicationLimit", MAX_BASIC_APPLICATIONS);

        // Count active applications
        long activeApplicationsCount = applicationRepository.findBySeeker(currentSeeker).stream()
                .filter(app -> !app.getStatus().equals("REJECTED") && !app.getStatus().equals("HIRED"))
                .count();
        model.addAttribute("activeApplicationsCount", activeApplicationsCount);

        // *** CRITICAL FIX: Calculate limitReached flag for the UI ***
        // If user is NOT premium AND has reached the limit
        boolean limitReached = !isPremiumOrPro && activeApplicationsCount >= MAX_BASIC_APPLICATIONS;
        model.addAttribute("limitReached", limitReached);

        // --- NORMALIZE incoming category parameter ---
        if (category != null) {
            category = category.trim();
            if (category.isEmpty() || category.equalsIgnoreCase("All Categories") || category.equalsIgnoreCase("All Category")) {
                category = null;
            }
        }

        // ----------------------------------------------------
        // --- 1. JOB LISTINGS DATA (AI MATCHING LOGIC) ---
        // ----------------------------------------------------
        String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword : null;
        boolean isRecommendedView = recommended != null && recommended.equalsIgnoreCase("true");

        if (isRecommendedView) {
            if (!isPremiumOrPro) {
                // If not premium, force them back to regular job search view
                model.addAttribute("errorMessage", "AI Job Matching requires a Premium subscription.");
                view = "jobs";
                isRecommendedView = false;
            } else if (profile == null || profile.getCompletenessScore() < AppConstants.PROFILE_COMPLETION_THRESHOLD) {
                model.addAttribute("errorMessage", "AI Matching requires your profile to be at least " + AppConstants.PROFILE_COMPLETION_THRESHOLD + "% complete.");
                view = "jobs";
                isRecommendedView = false;
            } else {
                // Use profile skills and title as the search keyword
                String skills = profile.getSkills() != null ? profile.getSkills() : "";
                String title = profile.getCurrentTitle() != null ? profile.getCurrentTitle() : "";

                // Combine skills and current title for a strong keyword match
                searchKeyword = String.join(" ", skills, title).trim().replaceAll("[^a-zA-Z0-9, ]", "");

                model.addAttribute("infoMessage", "Showing AI Recommended Jobs based on your profile skills.");
                model.addAttribute("recommendedView", true);
            }
        }

        List<JobRequirement> jobs = jobRepository.findJobsByCriteria(
                "ACTIVE",
                searchKeyword, // <--- NOW USES searchKeyword (either user input or AI-generated)
                (location != null && !location.isBlank()) ? location : null,
                category,
                (jobTypes != null && !jobTypes.isEmpty()) ? jobTypes : null
        );

        model.addAttribute("jobs", jobs);
        model.addAttribute("searchKeyword", searchKeyword); // <--- Use searchKeyword
        model.addAttribute("searchLocation", location);
        model.addAttribute("selectedJobTypes", jobTypes != null ? jobTypes : Collections.emptyList());
        model.addAttribute("selectedCategory", category != null ? category : "All Categories");

        int categoryCount = Math.min(AppConstants.ALL_JOB_CATEGORIES.size(), 10);
        List<String> displayedCategories = AppConstants.ALL_JOB_CATEGORIES.subList(0, categoryCount);

        model.addAttribute("jobCategories", displayedCategories);
        model.addAttribute("jobTypes", AppConstants.ALL_JOB_TYPES);
        // ----------------------------------------------------
        // --- END JOB LISTINGS DATA ---
        // ----------------------------------------------------

        // --- 2. APPLICATION TRACKER & MY CHATS DATA ---
        // FIX: Use the EAGER fetch method for dashboard stability (resolves SpEL Null Pointer)
        List<JobApplication> allApplications = applicationRepository.findBySeekerEagerly(currentSeeker);

        // Create a Set of Job IDs the user has already applied to for O(1) lookup in the view
        Set<Long> appliedJobIds = allApplications.stream()
                .map(app -> app.getJob().getId())
                .collect(Collectors.toSet());
        model.addAttribute("appliedJobIds", appliedJobIds);

        List<JobApplication> validApplications = allApplications.stream()
                .filter(app -> app.getJob() != null && app.getJob().getPostedBy() != null)
                .collect(Collectors.toList());

        List<JobApplication> activeChats = validApplications;

        model.addAttribute("applications", validApplications);
        model.addAttribute("activeChats", activeChats);

        List<Map<String, Object>> activeChatsJS = activeChats.stream().map(app -> {
            Map<String, Object> chatInfo = new HashMap<>();
            chatInfo.put("id", app.getId());
            // FIX: Use getter method (getJob()) instead of direct field access (job)
            chatInfo.put("jobTitle", app.getJob().getTitle());
            chatInfo.put("companyName", app.getJob().getPostedBy().getCompanyName());
            chatInfo.put("companyLogo", app.getJob().getPostedBy().getCompanyLogoFilename()); // Fixed access
            return chatInfo;
        }).collect(Collectors.toList());
        model.addAttribute("activeChatsJS", activeChatsJS);

        // --- 3. SUPPORT CHAT DATA ---
        List<Map<String, Object>> supportNotes = new ArrayList<>();
        if (profile != null) {
            String supportLog = profile.getSupportChatLog() != null ? profile.getSupportChatLog() : "[]";
            try {
                supportNotes = objectMapper.readValue(supportLog, new TypeReference<List<Map<String, Object>>>() {});
                supportNotes.add(0, createSeekerWelcomeMessage(currentSeeker.getFirstName()));
            } catch (IOException e) { /* ignore */ }
        }
        model.addAttribute("supportNotes", supportNotes);

        // --- 4. COMMON MODEL ATTRIBUTES ---
        model.addAttribute("user", currentSeeker);
        model.addAttribute("profile", profile);
        model.addAttribute("completeness", profile != null ? profile.getCompletenessScore() : 0);
        model.addAttribute("currentView", view != null ? view : "dashboard");

        return "seeker/dashboard";
    }


    @GetMapping("/applications/exam/mock/{appId}")
    @Transactional
    public String mockSubmitExam(@PathVariable Long appId,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findById(appId);

        if (appOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found.");
            return "redirect:/seeker/dashboard?view=tracker";
        }

        JobApplication application = appOpt.get();

        if (!application.getSeeker().getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access Denied.");
            return "redirect:/seeker/dashboard?view=tracker";
        }

        // This MUST be a JSON array of answer objects to prevent the frontend JS error.
        String submittedAnswersJson = "[{\"questionText\": \"Biggest challenge facing our industry today?\", \"answer\": \"AI Integration and talent retention is key for my next move.\"}, {\"questionText\": \"Describe a time you failed and what you learned from it.\", \"answer\": \"Missed a major project deadline due to poor delegation, learned to trust and empower my team members earlier.\"}, {\"questionText\": \"Technical Question specific to the Job.\", \"answer\": \"My technical expertise lies in Java and Spring Boot security configuration.\"}]";

        if (application.getExamSubmitted()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Exam already submitted.");
            return "redirect:/seeker/dashboard?view=chats&openAppId=" + appId;
        }

        try {
            // 1. Update the application record state
            application.setExamAnswers(submittedAnswersJson);
            application.setExamSubmitted(true);
            application.setStatus("REVIEWED"); // Move to REVIEWED status for the employer

            // 2. Log the submission message for the chat history
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentUser.getId());
            newNote.put("senderFirstName", currentUser.getFirstName());
            newNote.put("senderLastName", currentUser.getLastName());
            newNote.put("message", "The application exam is submitted and ready for your review.");
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "JOB_SEEKER");

            notes.add(newNote);
            application.setMessageLog(objectMapper.writeValueAsString(notes));

            // 3. Final Save/Flush the combined changes
            applicationRepository.saveAndFlush(application);

            redirectAttributes.addFlashAttribute("successMessage", "Exam submitted instantly! Status updated to REVIEWED.");

        } catch (Exception e) {
            System.err.println("Error exam submission for App ID " + appId + ": " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Submission failed due to a server error.");
        }

        // Redirect to the application chat detail view
        return "redirect:/seeker/dashboard?view=chats&openAppId=" + appId;
    }


    // ############ NEW: SEEKER EXAM SUBMISSION ENDPOINT (Original API logic remains) ############
    @PostMapping("/api/applications/{appId}/exam/submit")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> submitExamAnswers(@PathVariable Long appId,
                                                    @RequestBody Map<String, String> payload,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        // ... (Original logic for JSON submission remains here)
        User currentUser = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findById(appId);

        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JobApplication application = appOpt.get();

        if (!application.getSeeker().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("{\"status\": \"error\", \"message\": \"Access Denied.\"}");
        }

        String submittedAnswersJson = payload.get("answers");

        if (submittedAnswersJson == null || submittedAnswersJson.isBlank()) {
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"Answers cannot be empty.\"}");
        }

        if (application.getExamSubmitted()) {
            return ResponseEntity.status(400).body("{\"status\": \"error\", \"message\": \"Exam already submitted.\"}");
        }

        try {
            // Validate JSON format submitted by user before proceeding with updates
            objectMapper.readValue(submittedAnswersJson, new TypeReference<List<Map<String, Object>>>() {});

            // 1. Update the application record state
            application.setExamAnswers(submittedAnswersJson);
            application.setExamSubmitted(true);
            application.setStatus("REVIEWED"); // Move to REVIEWED status for the employer

            // 2. Log the submission message for the chat history
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentUser.getId());
            newNote.put("senderFirstName", currentUser.getFirstName());
            newNote.put("senderLastName", currentUser.getLastName());
            newNote.put("message", "I have completed and submitted the required application exam.");
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "JOB_SEEKER");

            notes.add(newNote);
            application.setMessageLog(objectMapper.writeValueAsString(notes));

            // 3. Final Save/Flush the combined changes
            applicationRepository.saveAndFlush(application);

            return ResponseEntity.ok("{\"status\": \"success\", \"message\": \"Exam submitted successfully!\"}");
        } catch (Exception e) {
            // Log for server debugging
            System.err.println("Error processing exam submission for App ID " + appId + ": " + e.getMessage());

            // Return a more descriptive error to the client
            String errorMsg = e.getMessage() != null && e.getMessage().contains("JSON") ?
                    "Submission failed: Your answer format is invalid JSON. Please check the structure (e.g., brackets, quotes)." :
                    "Submission failed due to a server error.";

            return ResponseEntity.status(500).body("{\"status\": \"error\", \"message\": \"" + errorMsg + "\"}");
        }
    }


    // ############ APPLICATION LOGIC (ApplyJob) ############

    @PostMapping("/jobs/apply/{jobId}")
    @Transactional
    public String applyForJob(@PathVariable Long jobId,
                              @AuthenticationPrincipal UserDetails userDetails,
                              RedirectAttributes redirectAttributes) {

        User seeker = userService.findByUsername(userDetails.getUsername());
        JobRequirement job = jobRepository.findById(jobId).orElse(null);

        if (seeker == null || seeker.getRole() == null || !seeker.getRole().getName().equals("JOB_SEEKER")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only Job Seekers can apply for jobs.");
            return "redirect:/jobs/" + jobId;
        }

        if (job == null || !"ACTIVE".equals(job.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Job not found or is no longer active.");
            return "redirect:/";
        }

        if (seeker.getSeekerProfile() == null || seeker.getSeekerProfile().getCompletenessScore() < AppConstants.PROFILE_COMPLETION_THRESHOLD) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your profile is incomplete! Please update your profile to at least " + AppConstants.PROFILE_COMPLETION_THRESHOLD + "% to apply.");
            redirectAttributes.addAttribute("jobId", jobId);
            return "redirect:/seeker/profile/edit";
        }

        if (applicationRepository.findBySeekerAndJob(seeker, job).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You have already applied to this job.");
            return "redirect:/jobs/" + jobId;
        }

        // --- CRITICAL: PREMIUM CHECK & ENFORCEMENT ---
        boolean isPremiumOrPro = seeker.getPremiumTier() != null &&
                (seeker.getPremiumTier().equals(AppConstants.TIER_PREMIUM) ||
                        seeker.getPremiumTier().equals(AppConstants.TIER_PRO));

        if (!isPremiumOrPro) {
            long activeApplicationsCount = applicationRepository.findBySeeker(seeker).stream()
                    .filter(app -> !app.getStatus().equals("REJECTED") && !app.getStatus().equals("HIRED"))
                    .count();

            // STOP HERE: If limit reached, DO NOT SAVE. Redirect immediately.
            if (activeApplicationsCount >= MAX_BASIC_APPLICATIONS) {
                redirectAttributes.addFlashAttribute("errorMessage", "Application Limit Reached! You have " + activeApplicationsCount + " active applications. Upgrade to Premium for unlimited.");
                // Redirect back to dashboard view 'jobs'
                return "redirect:/seeker/dashboard?view=jobs";
            }
        }
        // --- END PREMIUM CHECK ---

        JobApplication application = new JobApplication(seeker, job);

        try {
            User employer = job.getPostedBy();
            String seekerName = seeker.getFirstName();
            String companyName = (employer != null && employer.getCompanyName() != null) ? employer.getCompanyName() : "our team";
            String jobTitle = job.getTitle();

            String autoReplyMessage = "Hello " + seekerName + ",\n\n" +
                    "Thank you for your application for the \"" + jobTitle + "\" position. We have successfully received it and our team will review it shortly.\n\n" +
                    "You can track the status of your application here. We appreciate your interest in " + companyName + ".";

            Map<String, Object> autoMessage = new HashMap<>();
            autoMessage.put("senderId", (employer != null) ? employer.getId() : 0L);
            autoMessage.put("senderFirstName", companyName);
            autoMessage.put("senderLastName", "Hiring Team");
            autoMessage.put("message", autoReplyMessage);
            autoMessage.put("createdAt", LocalDate.now().toString());
            autoMessage.put("senderRole", "EMPLOYER");

            List<Map<String, Object>> messageList = new ArrayList<>();
            messageList.add(autoMessage);

            application.setMessageLog(objectMapper.writeValueAsString(messageList));

        } catch (IOException e) {
            application.setMessageLog("[]");
            System.err.println("Failed to create initial auto-reply: " + e.getMessage());
        }

        applicationRepository.saveAndFlush(application);

        redirectAttributes.addFlashAttribute("successMessage", "Your application has been submitted successfully!");
        return "redirect:/jobs/" + jobId;
    }

    // ############ AI CHAT ASSISTANT (Premium Controlled) ############
    @PostMapping("/api/ai/chat")
    @ResponseBody
    public ResponseEntity<String> handleAiChatRequest(@RequestBody Map<String, String> payload,
                                                      @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService.findByUsername(userDetails.getUsername());

        // 1. Premium Check
        boolean isPremiumOrPro = currentUser.getPremiumTier() != null &&
                (currentUser.getPremiumTier().equals(AppConstants.TIER_PREMIUM) ||
                        currentUser.getPremiumTier().equals(AppConstants.TIER_PRO));

        if (!isPremiumOrPro) {
            return ResponseEntity.status(403).body("{\"response\": \"This feature requires a SwiftHire Premium subscription.\"}");
        }

        String prompt = payload.get("message");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body("{\"response\": \"Message cannot be empty.\"}");
        }

        try {
            String aiResponse = aiAssistantService.getAiResponse(prompt);
            return ResponseEntity.ok("{\"response\": \"" + aiResponse.replace("\"", "\\\"").replace("\n", "\\n") + "\"}");
        } catch (Exception e) {
            System.err.println("AI Service Error: " + e.getMessage());
            return ResponseEntity.status(500).body("{\"response\": \"I apologize, the AI service is temporarily unavailable.\"}");
        }
    }

    // --- REDIRECT/CHAT/SUPPORT LOGIC ---

    @GetMapping("/application_tracker")
    public String seekerApplicationTracker() {
        return "redirect:/seeker/dashboard?view=tracker";
    }

    @GetMapping("/applications/inbox")
    public String seekerChatInbox() {
        return "redirect:/seeker/dashboard?view=chats";
    }

    private Map<String, Object> createSeekerWelcomeMessage(String seekerFirstName) {
        Map<String, Object> permanentWelcome = new HashMap<>();
        permanentWelcome.put("senderId", 1L); // Admin ID
        permanentWelcome.put("senderFirstName", "Global");
        permanentWelcome.put("senderLastName", "Admin");

        String message = "Welcome to SwiftHire, " + (seekerFirstName != null ? seekerFirstName : "Seeker") + "!\n\n" +
                "This support chat is for any technical questions you have about your account, your profile, or platform issues.\n\n" +
                "**Please note:** For questions about a *specific job or application*, please use the 'My Chats' feature to contact the employer directly.\n\n" +
                "We're here to help you succeed. Good luck!";

        permanentWelcome.put("message", message);
        permanentWelcome.put("createdAt", LocalDate.now().toString());
        permanentWelcome.put("senderRole", "ADMIN");
        return permanentWelcome;
    }

    @GetMapping("/api/support/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getSupportMessages(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        Optional<SeekerProfile> profileOpt = profileRepository.findByUser(currentUser);

        String chatLogJson = "[]";
        if (profileOpt.isPresent()) {
            SeekerProfile profile = profileOpt.get();
            chatLogJson = profile.getSupportChatLog() != null ? profile.getSupportChatLog() : "[]";
        }

        try {
            List<Map<String, Object>> messages = objectMapper.readValue(chatLogJson, new TypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> finalMessages = new ArrayList<>();
            finalMessages.add(createSeekerWelcomeMessage(currentUser.getFirstName()));
            finalMessages.addAll(messages);

            return ResponseEntity.ok(finalMessages);

        } catch (IOException e) {
            List<Map<String, Object>> finalMessages = new ArrayList<>();
            finalMessages.add(createSeekerWelcomeMessage(currentUser.getFirstName()));
            return ResponseEntity.ok(finalMessages);
        }
    }

    @PostMapping("/support/note/add")
    @Transactional
    public String addSupportNote(@RequestParam("message") String message,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        SeekerProfile profile = profileRepository.findByUser(currentUser)
                .orElse(new SeekerProfile(currentUser));

        if (message == null || message.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Message cannot be empty.");
            return "redirect:/seeker/dashboard?view=support";
        }

        try {
            String supportLog = profile.getSupportChatLog() != null ? profile.getSupportChatLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(supportLog, new TypeReference<List<Map<String, Object>>>() {});

            boolean isFirstMessage = notes.isEmpty();

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentUser.getId());
            newNote.put("senderFirstName", currentUser.getFirstName());
            newNote.put("senderLastName", currentUser.getLastName());
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "JOB_SEEKER");

            notes.add(newNote);

            if (isFirstMessage) {
                Map<String, Object> autoReply = new HashMap<>();
                autoReply.put("senderId", 0L);
                autoReply.put("senderFirstName", "Support");
                autoReply.put("senderLastName", "Bot");
                autoReply.put("message", "Thank you for contacting Support. Your message has been logged, and a live admin will review your inquiry shortly.");
                autoReply.put("createdAt", LocalDate.now().toString());
                autoReply.put("senderRole", "SYSTEM_BOT");
                notes.add(autoReply);
            }

            profile.setSupportChatLog(objectMapper.writeValueAsString(notes));
            profileRepository.saveAndFlush(profile);

            redirectAttributes.addFlashAttribute("successMessage", "Support message sent!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to send message due to data error.");
        }

        return "redirect:/seeker/dashboard?view=support";
    }

    @PostMapping("/api/support/note/add")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> postSupportMessageJson(@RequestBody Map<String, String> payload,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        String message = payload.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"Message cannot be empty.\"}");
        }

        User currentUser = userService.findByUsername(userDetails.getUsername());
        SeekerProfile profile = profileRepository.findByUser(currentUser)
                .orElse(new SeekerProfile(currentUser));

        try {
            String supportLog = profile.getSupportChatLog() != null ? profile.getSupportChatLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(supportLog, new TypeReference<List<Map<String, Object>>>() {});

            boolean isFirstMessage = notes.isEmpty();

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentUser.getId());
            newNote.put("senderFirstName", currentUser.getFirstName());
            newNote.put("senderLastName", currentUser.getLastName());
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "JOB_SEEKER");

            notes.add(newNote);

            if (isFirstMessage) {
                Map<String, Object> autoReply = new HashMap<>();
                autoReply.put("senderId", 0L);
                autoReply.put("senderFirstName", "Support");
                autoReply.put("senderLastName", "Bot");
                autoReply.put("message", "Thank you for contacting Support. Your message has been logged, and a live admin will review your inquiry shortly.");
                autoReply.put("createdAt", LocalDate.now().toString());
                autoReply.put("senderRole", "SYSTEM_BOT");
                notes.add(autoReply);
            }

            profile.setSupportChatLog(objectMapper.writeValueAsString(notes));
            profileRepository.saveAndFlush(profile);

            return ResponseEntity.ok("{\"status\": \"success\"}");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"status\": \"error\", \"message\": \"Failed to save message.\"}");
        }
    }

    // FIX: Redirection to avoid the missing seeker/application_action_guide template error.
    @GetMapping("/applications/{appId}/action")
    @Transactional(readOnly = true)
    public String viewApplicationActionGuide(@PathVariable Long appId,
                                             @AuthenticationPrincipal UserDetails userDetails,
                                             Model model,
                                             RedirectAttributes redirectAttributes) {

        //
        return "redirect:/seeker/dashboard?view=chats&openAppId=" + appId;
    }

    @GetMapping("/api/applications/{appId}/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> getApplicationMessages(@PathVariable Long appId,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findById(appId);

        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        JobApplication application = appOpt.get();

        if (!application.getSeeker().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("Access Denied");
        }

        List<Map<String, Object>> notes = new ArrayList<>();
        try {
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            // Return empty list on error
        }
        return ResponseEntity.ok(notes);
    }

    // FIX: Redirection to avoid the missing seeker/application_action_guide template error.
    @GetMapping("/applications/{appId}")
    @Transactional(readOnly = true)
    public String viewApplicationDetail(@PathVariable Long appId,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {

        // Redirect directly to the chat view
        return "redirect:/seeker/dashboard?view=chats&openAppId=" + appId;
    }

    @PostMapping("/applications/note/add")
    @Transactional
    public String addApplicationNote(@RequestParam("applicationId") Long applicationId,
                                     @RequestParam("message") String message,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {

        User currentUser = userService.findByUsername(userDetails.getUsername());

        Optional<JobApplication> appOpt = applicationRepository.findApplicationWithDetailsById(applicationId);

        if (appOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found.");
            return "redirect:/seeker/dashboard?view=tracker";
        }

        JobApplication application = appOpt.get();

        if (!application.getSeeker().getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access Denied.");
            return "redirect:/seeker/dashboard?view=tracker";
        }

        if (message == null || message.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Message cannot be empty.");
            return "redirect:/seeker/dashboard?view=chats&openAppId=" + applicationId;
        }

        try {
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentUser.getId());
            newNote.put("senderFirstName", currentUser.getFirstName());
            newNote.put("senderLastName", currentUser.getLastName());
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "JOB_SEEKER");

            notes.add(newNote);
            application.setMessageLog(objectMapper.writeValueAsString(notes));

            applicationRepository.saveAndFlush(application);

            redirectAttributes.addFlashAttribute("successMessage", "Message sent!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to send message due to data error.");
        }

        return "redirect:/seeker/dashboard?view=chats&openAppId=" + applicationId;
    }

    @PostMapping("/api/applications/{appId}/messages")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> postMessageJson(@PathVariable Long appId,
                                                  @RequestBody Map<String, String> payload,
                                                  @AuthenticationPrincipal UserDetails userDetails) {

        String message = payload.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"Message cannot be empty.\"}");
        }

        User currentSeeker = userService.findByUsername(userDetails.getUsername());
        Optional<JobApplication> appOpt = applicationRepository.findById(appId);

        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        JobApplication application = appOpt.get();

        if (!application.getSeeker().getId().equals(currentSeeker.getId())) {
            return ResponseEntity.status(403).body("{\"status\": \"error\", \"message\": \"Access Denied.\"}");
        }

        try {
            String messageLog = application.getMessageLog() != null ? application.getMessageLog() : "[]";
            List<Map<String, Object>> notes = objectMapper.readValue(messageLog, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", currentSeeker.getId());
            newNote.put("senderFirstName", currentSeeker.getFirstName());
            newNote.put("senderLastName", currentSeeker.getLastName());
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "JOB_SEEKER");

            notes.add(newNote);
            application.setMessageLog(objectMapper.writeValueAsString(notes));
            applicationRepository.saveAndFlush(application);

            return ResponseEntity.ok().body("{\"status\": \"success\"}");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"status\": \"error\", \"message\": \"Failed to serialize/deserialize chat log.\"}");
        }
    }

    // --- PROFILE/RESUME LOGIC ---

    @GetMapping("/profile/edit")
    @Transactional(readOnly = true)
    public String showProfileEditForm(@AuthenticationPrincipal UserDetails userDetails, Model model,
                                      @RequestParam(value = "jobId", required = false) Long jobId) {
        User currentSeeker = userService.findByUsername(userDetails.getUsername());
        Optional<SeekerProfile> profileOpt = profileRepository.findByUser(currentSeeker);

        SeekerProfile profile = profileOpt.orElse(new SeekerProfile(currentSeeker));
        if (profile.getUser() == null) {
            profile.setUser(currentSeeker);
        }

        model.addAttribute("profile", profile);
        model.addAttribute("setupMode", profileOpt.isEmpty());
        model.addAttribute("user", currentSeeker);
        model.addAttribute("completeness", profile.getCompletenessScore());

        model.addAttribute("jobId", jobId);

        // --- LOAD LIST DATA FOR EDITING ---
        try {
            String workJson = profile.getWorkExperienceJson() != null ? profile.getWorkExperienceJson() : "[]";
            String eduJson = profile.getEducationJson() != null ? profile.getEducationJson() : "[]";

            List<WorkExperience> workExperiences = objectMapper.readValue(workJson, new TypeReference<List<SeekerController.WorkExperience>>() {});
            List<Education> educationHistory = objectMapper.readValue(eduJson, new TypeReference<List<SeekerController.Education>>() {});
            model.addAttribute("workExperiences", workExperiences);
            model.addAttribute("educationHistory", educationHistory);
        } catch (IOException e) {
            model.addAttribute("workExperiences", new ArrayList<>());
            model.addAttribute("educationHistory", new ArrayList<>());
            model.addAttribute("errorMessage", "Error loading profile history data: " + e.getMessage());
        }
        // --- END LOAD LIST DATA ---

        return "seeker/profile_setup";
    }

    @PostMapping("/profile/save")
    @Transactional
    public String saveProfile(
            @ModelAttribute("profile") SeekerProfile profileFormData,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            @RequestParam(value = "profilePictureFile", required = false) MultipartFile profilePictureFile,
            RedirectAttributes redirectAttributes,
            @RequestParam(value = "offeringServices", required = false) Boolean offeringServices,
            @RequestParam(value = "jobId", required = false) Long jobId
    ) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        SeekerProfile seekerProfile = profileRepository.findByUser(currentUser)
                .orElse(new SeekerProfile(currentUser));

        // 1. update user
        if (fullName != null) {
            String[] names = fullName.split(" ", 2);
            currentUser.setFirstName(names.length > 0 ? names[0] : "");
            currentUser.setLastName(names.length > 1 ? names[1] : "");
        }
        if (phone != null) {
            currentUser.setPhoneNumber(phone);
        }

        // 2. update seeker profile fields
        seekerProfile.setCity(profileFormData.getCity());
        seekerProfile.setPreferredLocation(profileFormData.getPreferredLocation());
        seekerProfile.setCurrentTitle(profileFormData.getCurrentTitle());
        seekerProfile.setYearsExperience(profileFormData.getYearsExperience());
        seekerProfile.setSkills(profileFormData.getSkills());
        seekerProfile.setJobType(profileFormData.getJobType());
        seekerProfile.setExpectedSalary(profileFormData.getExpectedSalary());
        seekerProfile.setProfileHeadline(profileFormData.getProfileHeadline());
        seekerProfile.setOfferingServices(offeringServices != null && offeringServices);

        // 3. handle file uploads
        try {
            if (resumeFile != null && !resumeFile.isEmpty()) {
                String filename = fileService.saveResumeFile(resumeFile, currentUser.getId());
                seekerProfile.setResumeFilename(filename);
            }

            if (profilePictureFile != null && !profilePictureFile.isEmpty()) {
                String picFilename = fileService.saveProfilePictureFile(profilePictureFile, currentUser.getId());
                currentUser.setProfilePictureFilename(picFilename);
            }

        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving file: " + e.getMessage());
            return "redirect:/seeker/profile/edit";
        }

        // 4. final save and redirect
        seekerProfile.calculateCompleteness();
        profileRepository.saveAndFlush(seekerProfile);
        userService.saveUser(currentUser);

        redirectAttributes.addFlashAttribute("successMessage", "Profile successfully updated!");

        if (jobId != null && seekerProfile.getCompletenessScore() >= AppConstants.PROFILE_COMPLETION_THRESHOLD) {
            return "redirect:/jobs/" + jobId;
        }

        return "redirect:/seeker/dashboard?view=tracker";
    }

    // --- AJAX endpoints for work/education lists (add/delete) ---

    @PostMapping("/api/experience/add")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> addWorkExperienceJson(
            @RequestBody WorkExperience newExp,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        SeekerProfile seekerProfile = profileRepository.findByUser(currentUser)
                .orElse(new SeekerProfile(currentUser));

        try {
            String workJson = seekerProfile.getWorkExperienceJson() != null ? seekerProfile.getWorkExperienceJson() : "[]";
            List<WorkExperience> experiences = objectMapper.readValue(workJson, new TypeReference<List<SeekerController.WorkExperience>>() {});

            newExp.id = System.currentTimeMillis();
            experiences.add(newExp);
            seekerProfile.setWorkExperienceJson(objectMapper.writeValueAsString(experiences));

            seekerProfile.calculateCompleteness();
            profileRepository.saveAndFlush(seekerProfile);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Experience added successfully!");
            response.put("newId", newExp.id);
            response.put("completeness", seekerProfile.getCompletenessScore());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error processing data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/api/experience/delete/{expId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteWorkExperienceJson(
            @PathVariable Long expId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        SeekerProfile seekerProfile = profileRepository.findByUser(currentUser)
                .orElse(new SeekerProfile(currentUser));

        try {
            String workJson = seekerProfile.getWorkExperienceJson() != null ? seekerProfile.getWorkExperienceJson() : "[]";
            List<WorkExperience> experiences = objectMapper.readValue(workJson, new TypeReference<List<SeekerController.WorkExperience>>() {});

            boolean removed = experiences.removeIf(exp -> exp.id.equals(expId));

            if (removed) {
                seekerProfile.setWorkExperienceJson(objectMapper.writeValueAsString(experiences));
                seekerProfile.calculateCompleteness();
                profileRepository.saveAndFlush(seekerProfile);

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Experience removed.");
                response.put("completeness", seekerProfile.getCompletenessScore());

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Experience not found.");
                return ResponseEntity.status(404).body(response);
            }
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error deleting data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/education/add")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> addEducationJson(
            @RequestBody Education newEdu,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        SeekerProfile seekerProfile = profileRepository.findByUser(currentUser)
                .orElse(new SeekerProfile(currentUser));

        try {
            String eduJson = seekerProfile.getEducationJson() != null ? seekerProfile.getEducationJson() : "[]";
            List<Education> educationHistory = objectMapper.readValue(eduJson, new TypeReference<List<SeekerController.Education>>() {});

            newEdu.id = System.currentTimeMillis();
            educationHistory.add(newEdu);
            seekerProfile.setEducationJson(objectMapper.writeValueAsString(educationHistory));

            seekerProfile.calculateCompleteness();
            profileRepository.saveAndFlush(seekerProfile);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Education added successfully!");
            response.put("newId", newEdu.id);
            response.put("completeness", seekerProfile.getCompletenessScore());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error processing data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/api/education/delete/{eduId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteEducationJson(
            @PathVariable Long eduId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService.findByUsername(userDetails.getUsername());
        SeekerProfile seekerProfile = profileRepository.findByUser(currentUser)
                .orElse(new SeekerProfile(currentUser));

        try {
            String eduJson = seekerProfile.getEducationJson() != null ? seekerProfile.getEducationJson() : "[]";
            List<Education> educationHistory = objectMapper.readValue(eduJson, new TypeReference<List<SeekerController.Education>>() {});

            boolean removed = educationHistory.removeIf(edu -> edu.id.equals(eduId));

            if (removed) {
                seekerProfile.setEducationJson(objectMapper.writeValueAsString(educationHistory));
                seekerProfile.calculateCompleteness();
                profileRepository.saveAndFlush(seekerProfile);

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Education removed.");
                response.put("completeness", seekerProfile.getCompletenessScore());

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Education not found.");
                return ResponseEntity.status(404).body(response);
            }
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error deleting data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/resume/view")
    public ResponseEntity<Resource> downloadResume(@AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findByUsername(userDetails.getUsername());

        Optional<SeekerProfile> profileOpt = profileRepository.findByUser(currentUser);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SeekerProfile profile = profileOpt.get();

        if (profile.getResumeFilename() == null || profile.getResumeFilename().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = fileService.loadFileAsResource(FileService.RESUME_SUBDIR, profile.getResumeFilename());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/profile/pic/{userId}")
    @ResponseBody
    public ResponseEntity<Resource> getProfilePicture(@PathVariable Long userId) {
        User user = userService.findById(userId);
        if (user == null || user.getProfilePictureFilename() == null || user.getProfilePictureFilename().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = fileService.loadFileAsResource(FileService.PROFILE_PIC_SUBDIR, user.getProfilePictureFilename());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.notFound().build();
        }
    }
}