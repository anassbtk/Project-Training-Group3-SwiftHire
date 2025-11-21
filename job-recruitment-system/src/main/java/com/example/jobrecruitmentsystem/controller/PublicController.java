package com.example.jobrecruitmentsystem.controller;

import com.example.jobrecruitmentsystem.model.JobApplication;
import com.example.jobrecruitmentsystem.model.JobRequirement;
import com.example.jobrecruitmentsystem.model.SeekerProfile;
import com.example.jobrecruitmentsystem.model.User;
import com.example.jobrecruitmentsystem.repository.JobApplicationRepository;
import com.example.jobrecruitmentsystem.repository.JobRequirementRepository;
import com.example.jobrecruitmentsystem.repository.SeekerProfileRepository;
import com.example.jobrecruitmentsystem.repository.UserRepository;
import com.example.jobrecruitmentsystem.service.UserService;
import com.example.jobrecruitmentsystem.service.AiAssistantService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Random;

@Controller
public class PublicController {

    private final UserService userService;
    private final JobRequirementRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final SeekerProfileRepository seekerProfileRepository;
    private final UserRepository userRepository;
    private final AiAssistantService aiAssistantService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_BASIC_APPLICATIONS = 10;

    public PublicController(UserService userService,
                            JobRequirementRepository jobRepository,
                            JobApplicationRepository applicationRepository,
                            SeekerProfileRepository seekerProfileRepository,
                            UserRepository userRepository,
                            AiAssistantService aiAssistantService) {
        this.userService = userService;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.seekerProfileRepository = seekerProfileRepository;
        this.userRepository = userRepository;
        this.aiAssistantService = aiAssistantService;
    }

    private Map<String, Long> getMonthlyStats(List<User> allUsers) {
        YearMonth currentMonth = YearMonth.now();
        long newJobsThisMonth = jobRepository.findAll().stream()
                .filter(job -> job.getPostedAt() != null && YearMonth.from(job.getPostedAt()).equals(currentMonth))
                .count();
        long newSeekersThisMonth = allUsers.stream()
                .filter(u -> u.getRole() != null && "JOB_SEEKER".equals(u.getRole().getName()))
                .filter(u -> u.getCreatedAt() != null && YearMonth.from(u.getCreatedAt()).equals(currentMonth))
                .count();
        long newEmployersThisMonth = allUsers.stream()
                .filter(u -> u.getRole() != null && "EMPLOYER".equals(u.getRole().getName()))
                .filter(u -> u.getCreatedAt() != null && YearMonth.from(u.getCreatedAt()).equals(currentMonth))
                .count();
        long applicationsThisMonth = applicationRepository.findAll().stream()
                .filter(app -> app.getAppliedAt() != null && YearMonth.from(app.getAppliedAt()).equals(currentMonth))
                .count();
        long pendingJobsCount = jobRepository.findByStatus("PENDING_ADMIN").size();

        Map<String, Long> stats = new HashMap<>();
        stats.put("newJobs", newJobsThisMonth);
        stats.put("newSeekers", newSeekersThisMonth);
        stats.put("newEmployers", newEmployersThisMonth);
        stats.put("applicationsThisMonth", applicationsThisMonth);
        stats.put("pendingJobsCount", pendingJobsCount);
        return stats;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public String home(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() &&
                !(authentication.getPrincipal() instanceof String) &&
                !(authentication.getPrincipal().equals("anonymousUser"));

        User currentUser = null;
        if (isAuthenticated && authentication.getPrincipal() instanceof UserDetails) {
            String username = authentication.getName();
            currentUser = userService.findByUsername(username);
            if (currentUser != null) {
                model.addAttribute("user", currentUser);
            }
        }

        if (currentUser != null && currentUser.getRole() != null && "ADMIN".equals(currentUser.getRole().getName())) {
            List<User> allUsers = userRepository.findAll();
            Map<String, Long> monthlyStats = getMonthlyStats(allUsers);
            model.addAttribute("monthlyStats", monthlyStats);
        } else {
            model.addAttribute("monthlyStats", null);
        }

        List<JobRequirement> activeJobs = jobRepository.findByStatus("ACTIVE");

        int categoryCount = Math.min(AppConstants.ALL_JOB_CATEGORIES.size(), 10);
        List<String> displayedCategories = AppConstants.ALL_JOB_CATEGORIES.subList(0, categoryCount);
        model.addAttribute("jobCategories", displayedCategories);

        Random random = new Random();
        List<Integer> jobCategoryCounts = new ArrayList<>();
        for (int i = 0; i < categoryCount; i++) {
            jobCategoryCounts.add(random.nextInt(500) + 300);
        }
        model.addAttribute("jobCategoryCounts", jobCategoryCounts);

        int skillCount = Math.min(AppConstants.PROMINENT_SKILLS.size(), 10);
        List<String> displayedSkills = AppConstants.PROMINENT_SKILLS.subList(0, skillCount);
        model.addAttribute("prominentSkills", displayedSkills);
        model.addAttribute("skillCounts", AppConstants.MOCK_SKILL_COUNTS);

        model.addAttribute("featuredJobs", activeJobs.subList(0, Math.min(activeJobs.size(), 5)));
        model.addAttribute("title", "Welcome to the Job Recruitment System!");

        return "index";
    }

    @GetMapping("/about")
    public String showAboutPage() {
        return "about";
    }

    @GetMapping("/contact")
    public String showContactPage() {
        return "contact";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot_password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam("email") String email, RedirectAttributes redirectAttributes) {
        User user = userService.findByEmail(email);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: Account not found with that email.");
            return "redirect:/forgot-password";
        }
        redirectAttributes.addAttribute("username", user.getUsername());
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("username") String username, Model model) {
        User user = userService.findByUsername(username);
        if (user == null || user.getSecurityQuestion() == null || user.getSecurityQuestion().isBlank()) {
            model.addAttribute("errorMessage", "Error: Account not found or security question is not set.");
            return "forgot_password";
        }
        String questionText = AppConstants.SECURITY_QUESTIONS.getOrDefault(user.getSecurityQuestion(), "Your security question:");
        model.addAttribute("username", username);
        model.addAttribute("securityQuestionText", questionText);
        model.addAttribute("securityQuestionKey", user.getSecurityQuestion());
        return "reset_password";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(@RequestParam("username") String username,
                                      @RequestParam("securityQuestionKey") String questionKey,
                                      @RequestParam("answer") String answer,
                                      @RequestParam("password") String password,
                                      @RequestParam("confirmPassword") String confirmPassword,
                                      RedirectAttributes redirectAttributes) {
        User user = userService.findByUsername(username);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: Account not found.");
            return "redirect:/forgot-password";
        }
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: Passwords do not match.");
            redirectAttributes.addAttribute("username", username);
            return "redirect:/reset-password";
        }
        if (user.getSecurityQuestion() == null || !user.getSecurityQuestion().equals(questionKey) ||
                user.getSecurityAnswer() == null || !user.getSecurityAnswer().trim().equalsIgnoreCase(answer.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: Security answer is incorrect.");
            redirectAttributes.addAttribute("username", username);
            return "redirect:/reset-password";
        }
        userService.updatePassword(user.getUsername(), password);
        redirectAttributes.addFlashAttribute("successMessage", "Password successfully reset. Please log in.");
        return "redirect:/login";
    }

    @GetMapping("/api/jobs/suggest")
    @ResponseBody
    public List<String> getJobSuggestions(@RequestParam("query") String query) {
        if (query == null || query.length() < 3) {
            return Collections.emptyList();
        }
        List<JobRequirement> jobs = jobRepository.findByStatusAndTitleContainingIgnoreCase("ACTIVE", query);
        return jobs.stream()
                .map(JobRequirement::getTitle)
                .distinct()
                .collect(Collectors.toList());
    }

    @GetMapping("/jobs/hub")
    public String showJobSearchHub(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "jobType", required = false) List<String> jobTypes,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "category", required = false) String category,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {

        Map<String, ?> inputFlashMap = RequestContextUtils.getInputFlashMap(request);
        if (inputFlashMap != null) {
            if(inputFlashMap.containsKey("successMessage")) {
                redirectAttributes.addFlashAttribute("successMessage", inputFlashMap.get("successMessage"));
            }
            if(inputFlashMap.containsKey("errorMessage")) {
                redirectAttributes.addFlashAttribute("errorMessage", inputFlashMap.get("errorMessage"));
            }
        }

        if (keyword != null) redirectAttributes.addAttribute("keyword", keyword);
        if (jobTypes != null) redirectAttributes.addAttribute("jobType", jobTypes);
        if (location != null) redirectAttributes.addAttribute("location", location);
        if (category != null) redirectAttributes.addAttribute("category", category);

        redirectAttributes.addAttribute("view", "jobs");
        return "redirect:/seeker/dashboard";
    }

    // --- NEW API ENDPOINT FOR BUTTON CLICK ---
    @GetMapping("/api/jobs/{id}/match")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> calculateMatchScore(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User user = userService.findByUsername(userDetails.getUsername());

        // 1. Check Premium Status
        boolean isPremium = user.getPremiumTier() != null &&
                (user.getPremiumTier().equals(AppConstants.TIER_PREMIUM) ||
                        user.getPremiumTier().equals(AppConstants.TIER_PRO));

        if (!isPremium) {
            return ResponseEntity.status(403).body(Map.of("error", "Premium subscription required"));
        }

        // 2. Get Job
        Optional<JobRequirement> jobOpt = jobRepository.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 3. Get Profile
        SeekerProfile profile = user.getSeekerProfile();
        if (profile == null || profile.getCompletenessScore() < 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile incomplete"));
        }

        // 4. Perform Analysis (Costly Operation)
        String profileText = "Title: " + profile.getCurrentTitle() +
                ", Skills: " + profile.getSkills() +
                ", Experience: " + profile.getYearsExperience() + " years" +
                ", Headline: " + profile.getProfileHeadline();

        Map<String, Object> result = aiAssistantService.analyzeCandidateMatch(jobOpt.get().getDescription(), profileText);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/jobs/{id}")
    @Transactional(readOnly = true)
    public String viewJobDetail(@PathVariable Long id, Model model) {
        Optional<JobRequirement> jobOpt = jobRepository.findById(id);

        if (jobOpt.isPresent() && (jobOpt.get().getStatus().equals("ACTIVE") || jobOpt.get().getStatus().equals("PENDING_ADMIN"))) {
            JobRequirement job = jobOpt.get();
            model.addAttribute("job", job);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication.getPrincipal() instanceof String);
            model.addAttribute("isAuthenticated", isAuthenticated);

            if (isAuthenticated && authentication.getPrincipal() instanceof UserDetails) {
                User user = userService.findByUsername(authentication.getName());

                if (user != null && user.getRole().getName().equals("JOB_SEEKER")) {
                    model.addAttribute("isJobSeeker", true);
                    model.addAttribute("currentUser", user);

                    Optional<JobApplication> applicationOpt = applicationRepository.findBySeekerAndJob(user, job);
                    boolean alreadyApplied = applicationOpt.isPresent();
                    model.addAttribute("alreadyApplied", alreadyApplied);

                    if (alreadyApplied) {
                        model.addAttribute("applicationId", applicationOpt.get().getId());
                        model.addAttribute("applicationStatus", applicationOpt.get().getStatus());
                    }

                    boolean profileComplete = user.getSeekerProfile() != null && user.getSeekerProfile().getCompletenessScore() >= AppConstants.PROFILE_COMPLETION_THRESHOLD;
                    model.addAttribute("profileComplete", profileComplete);
                    model.addAttribute("profileCompleteness", user.getSeekerProfile() != null ? user.getSeekerProfile().getCompletenessScore() : 0);

                    // --- PREMIUM CHECK ---
                    boolean isPremium = user.getPremiumTier() != null && (user.getPremiumTier().equals(AppConstants.TIER_PREMIUM) || user.getPremiumTier().equals(AppConstants.TIER_PRO));
                    model.addAttribute("isPremium", isPremium);

                    if (!isPremium) {
                        long activeApps = applicationRepository.findBySeeker(user).stream()
                                .filter(app -> !app.getStatus().equals("REJECTED") && !app.getStatus().equals("HIRED"))
                                .count();
                        model.addAttribute("limitReached", activeApps >= MAX_BASIC_APPLICATIONS);
                    } else {
                        model.addAttribute("limitReached", false);
                    }

                } else {
                    model.addAttribute("isJobSeeker", false);
                }
            } else {
                model.addAttribute("isJobSeeker", false);
            }

            return "job_detail";
        }

        return "redirect:/";
    }

    @PostMapping("/jobs/apply/{jobId}")
    @Transactional
    public String applyForJob(@PathVariable Long jobId,
                              @AuthenticationPrincipal UserDetails userDetails,
                              RedirectAttributes redirectAttributes) {

        User seeker = userService.findByUsername(userDetails.getUsername());
        JobRequirement job = jobRepository.findById(jobId).orElse(null);

        if (seeker == null || !seeker.getRole().getName().equals("JOB_SEEKER")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only Job Seekers can apply for jobs.");
            return "redirect:/jobs/" + jobId;
        }

        if (job == null || !job.getStatus().equals("ACTIVE")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Job not found or is no longer active.");
            return "redirect:/";
        }

        boolean isPremium = seeker.getPremiumTier() != null && (seeker.getPremiumTier().equals(AppConstants.TIER_PREMIUM) || seeker.getPremiumTier().equals(AppConstants.TIER_PRO));
        if (!isPremium) {
            long activeApps = applicationRepository.findBySeeker(seeker).stream()
                    .filter(app -> !app.getStatus().equals("REJECTED") && !app.getStatus().equals("HIRED"))
                    .count();

            if (activeApps >= MAX_BASIC_APPLICATIONS) {
                redirectAttributes.addFlashAttribute("errorMessage", "Application limit reached (10/10). Upgrade to Premium to continue applying.");
                return "redirect:/jobs/" + jobId;
            }
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

        JobApplication application = new JobApplication(seeker, job);

        try {
            User employer = job.getPostedBy();
            String seekerName = seeker.getFirstName();
            String companyName = (employer != null && employer.getCompanyName() != null) ? employer.getCompanyName() : "our team";
            String jobTitle = job.getTitle();

            String autoReplyMessage = "Hello " + seekerName + ",\n\n" +
                    "Thank you for your application for the **" + jobTitle + "** position. We have successfully received it and our team will review it shortly.\n\n" +
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
        }

        applicationRepository.saveAndFlush(application);

        redirectAttributes.addFlashAttribute("successMessage", "Your application has been submitted successfully!");
        return "redirect:/jobs/" + jobId;
    }

    @GetMapping("/register")
    public String showRegistrationForm() {
        return "redirect:/login";
    }

    @PostMapping("/register")
    @Transactional
    public String registerUser(@ModelAttribute("user") User user,
                               @RequestParam("roleType") String roleType,
                               @RequestParam(value = "companyName", required = false) String companyName,
                               Model model) {
        try {
            userService.registerNewUser(user, roleType, companyName);
            model.addAttribute("successMessage", "Registration successful! Please log in.");
            model.addAttribute("user", new User());
            return "login";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("user", user);
            return "login";
        }
    }

    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new User());
        }
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out successfully.");
        }
        return "login";
    }
}