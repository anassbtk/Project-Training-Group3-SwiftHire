package com.example.jobrecruitmentsystem.controller;

import com.example.jobrecruitmentsystem.model.JobApplication;
import com.example.jobrecruitmentsystem.model.JobRequirement;
import com.example.jobrecruitmentsystem.model.Role;
import com.example.jobrecruitmentsystem.model.SeekerProfile;
import com.example.jobrecruitmentsystem.model.User;
import com.example.jobrecruitmentsystem.repository.JobApplicationRepository;
import com.example.jobrecruitmentsystem.repository.JobRequirementRepository;
import com.example.jobrecruitmentsystem.repository.RoleRepository;
import com.example.jobrecruitmentsystem.repository.SeekerProfileRepository;
import com.example.jobrecruitmentsystem.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final JobRequirementRepository jobRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SeekerProfileRepository seekerProfileRepository;
    private final JobApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // FIXED: Using constant from AppConstants now
    private final List<String> JOB_TYPES = AppConstants.ALL_JOB_TYPES;
    private final List<String> JOB_CATEGORIES = AppConstants.ALL_JOB_CATEGORIES;

    public AdminController(JobRequirementRepository jobRepository,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           SeekerProfileRepository seekerProfileRepository,
                           JobApplicationRepository applicationRepository) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.seekerProfileRepository = seekerProfileRepository;
        this.applicationRepository = applicationRepository;
    }

    /**
     * Helper method to calculate monthly statistics.
     */
    private Map<String, Long> getMonthlyStats(List<User> allUsers) {
        YearMonth currentMonth = YearMonth.now();

        // Count jobs posted this month
        long newJobsThisMonth = jobRepository.findAll().stream()
                .filter(job -> job.getPostedAt() != null && YearMonth.from(job.getPostedAt()).equals(currentMonth))
                .count();

        // Count new seekers this month
        long newSeekersThisMonth = allUsers.stream()
                .filter(u -> u.getRole() != null && "JOB_SEEKER".equals(u.getRole().getName()))
                .filter(u -> u.getCreatedAt() != null && YearMonth.from(u.getCreatedAt()).equals(currentMonth))
                .count();

        // Count new employers this month
        long newEmployersThisMonth = allUsers.stream()
                .filter(u -> u.getRole() != null && "EMPLOYER".equals(u.getRole().getName()))
                .filter(u -> u.getCreatedAt() != null && YearMonth.from(u.getCreatedAt()).equals(currentMonth))
                .count();

        // Count applications created this month
        long applicationsThisMonth = applicationRepository.findAll().stream()
                .filter(app -> app.getAppliedAt() != null && YearMonth.from(app.getAppliedAt()).equals(currentMonth))
                .count();

        // Count pending jobs (FIXED: Using findByStatus().size() to resolve the compile error)
        long pendingJobsCount = jobRepository.findByStatus("PENDING_ADMIN").size();

        Map<String, Long> stats = new HashMap<>();
        stats.put("newJobs", newJobsThisMonth);
        stats.put("newSeekers", newSeekersThisMonth);
        stats.put("newEmployers", newEmployersThisMonth);
        stats.put("applicationsThisMonth", applicationsThisMonth);
        stats.put("pendingJobsCount", pendingJobsCount);
        return stats;
    }

    /**
     * Exposes monthlyStats to the model for use on any page if the user is an Admin.
     */
    @ModelAttribute("monthlyStats")
    public Map<String, Long> getMonthlyStatsForIndex() {
        // Check if the current user is authenticated as an ADMIN
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            return getMonthlyStats(userRepository.findAll());
        }
        // Return null if not an Admin, which is handled safely by index.html
        return null;
    }

    /**
     * Handles the Admin Dashboard: shows PENDING jobs and ACTIVE jobs, plus overall stats.
     * URL: /admin/dashboard
     */
    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public String adminDashboard(Model model) {
        List<JobRequirement> pendingJobs = jobRepository.findByStatus("PENDING_ADMIN");
        List<JobRequirement> activeJobs = jobRepository.findByStatus("ACTIVE");
        List<User> allUsers = userRepository.findAll(); // Fetch all users for stats

        long totalUserCount = allUsers.size();

        Map<String, Long> roleCounts = allUsers.stream()
                .collect(Collectors.groupingBy(u -> u.getRole() != null ? u.getRole().getName() : "UNASSIGNED", Collectors.counting()));

        Map<String, Long> monthlyStats = getMonthlyStats(allUsers); // Calculate monthly stats

        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("activeJobs", activeJobs);
        model.addAttribute("totalUserCount", totalUserCount);
        model.addAttribute("userRoleCounts", roleCounts); // Data for doughnut chart
        model.addAttribute("monthlyStats", monthlyStats); // Data for new monthly chart

        // Provide categories/types to the admin dashboard for any filters or displays
        int categoryCount = Math.min(JOB_CATEGORIES.size(), 10);
        model.addAttribute("jobCategories", JOB_CATEGORIES.subList(0, categoryCount));
        model.addAttribute("jobTypes", JOB_TYPES);

        return "admin/dashboard";
    }


    /**
     * Handles the action to approve a job (sets status to ACTIVE).
     * URL: /admin/approve/{jobId}
     */
    @GetMapping("/approve/{jobId}")
    @Transactional
    public String approveJob(@PathVariable Long jobId, RedirectAttributes redirectAttributes) {
        Optional<JobRequirement> jobOpt = jobRepository.findById(jobId);

        if (jobOpt.isPresent()) {
            JobRequirement job = jobOpt.get();
            job.setStatus("ACTIVE");
            jobRepository.save(job);
            redirectAttributes.addFlashAttribute("successMessage", "Job ID " + job.getId() + " approved and set to ACTIVE.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Job not found.");
        }

        return "redirect:/admin/dashboard";
    }


    /**
     * Handles the action to delete a job.
     * This now also deletes all related applications to prevent errors.
     * URL: /admin/delete/{jobId}
     */
    @GetMapping("/delete/{jobId}")
    @Transactional
    public String deleteJob(@PathVariable Long jobId, RedirectAttributes redirectAttributes) {
        Optional<JobRequirement> jobOpt = jobRepository.findById(jobId);

        if (jobOpt.isPresent()) {
            try {
                JobRequirement job = jobOpt.get();

                // 1. Find all applications linked to this job
                List<JobApplication> applications = applicationRepository.findByJob(job);

                // 2. Delete all those applications first (if any exist)
                if (applications != null && !applications.isEmpty()) {
                    applicationRepository.deleteAll(applications);
                }

                // 3. Now it's safe to delete the job
                jobRepository.delete(job);

                redirectAttributes.addFlashAttribute("successMessage", "Job ID " + jobId + " and all its applications successfully deleted.");

            } catch (Exception e) {
                // This catch block is still a good safety net
                redirectAttributes.addFlashAttribute("errorMessage", "Could not delete job. An unexpected error occurred: " + e.getMessage());
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Job not found.");
        }

        return "redirect:/admin/dashboard";
    }

    // --- NEW METHOD 1: SHOW EDIT JOB FORM ---
    /**
     * GET: Shows the form to edit an existing job.
     * URL: /admin/job/edit/{jobId}
     */
    @GetMapping("/job/edit/{jobId}")
    @Transactional(readOnly = true)
    public String showEditJobForm(@PathVariable Long jobId, Model model, RedirectAttributes redirectAttributes) {
        Optional<JobRequirement> jobOpt = jobRepository.findById(jobId);

        if (jobOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Job not found with ID: " + jobId);
            return "redirect:/admin/dashboard";
        }

        model.addAttribute("job", jobOpt.get());
        model.addAttribute("jobTypes", JOB_TYPES); // Add job types for the dropdown
        model.addAttribute("jobCategories", JOB_CATEGORIES); // Add categories for the dropdown
        return "admin/edit_job";
    }

    // --- NEW METHOD 2: HANDLE JOB UPDATE ---
    /**
     * POST: Saves the changes to the job.
     * URL: /admin/job/edit/{id}
     *
     * NOTE: requiredSkills is read via request param to avoid compilation issues if the JobRequirement model
     * on the classpath does not expose a getter named getRequiredSkills(). The form should submit a field
     * named 'requiredSkills' (this matches the hidden input used in the employer new_job form).
     */
    @PostMapping("/job/edit/{id}")
    @Transactional
    public String handleUpdateJob(@PathVariable("id") Long jobId,
                                  @ModelAttribute("job") JobRequirement jobDetailsFromForm,
                                  @RequestParam(value = "requiredSkills", required = false) String requiredSkills,
                                  RedirectAttributes redirectAttributes) {

        Optional<JobRequirement> existingJobOpt = jobRepository.findById(jobId);

        if (existingJobOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Job not found. Could not update.");
            return "redirect:/admin/dashboard";
        }

        JobRequirement existingJob = existingJobOpt.get();

        existingJob.setTitle(jobDetailsFromForm.getTitle());
        existingJob.setDescription(jobDetailsFromForm.getDescription());
        // Safely set requiredSkills using request param (may be null)
        existingJob.setRequiredSkills(requiredSkills != null ? requiredSkills : null);
        existingJob.setLocationCity(jobDetailsFromForm.getLocationCity());
        existingJob.setJobType(jobDetailsFromForm.getJobType());
        existingJob.setSalaryMin(jobDetailsFromForm.getSalaryMin());
        existingJob.setSalaryMax(jobDetailsFromForm.getSalaryMax());
        existingJob.setRemoteOption(jobDetailsFromForm.getRemoteOption() != null && jobDetailsFromForm.getRemoteOption());

        // Normalize and set category (treat empty string as null so filters won't break)
        String incomingCategory = jobDetailsFromForm.getJobCategory();
        if (incomingCategory != null) {
            incomingCategory = incomingCategory.trim();
            if (incomingCategory.isEmpty()) {
                existingJob.setJobCategory(null);
            } else {
                existingJob.setJobCategory(incomingCategory);
            }
        } else {
            existingJob.setJobCategory(null);
        }

        jobRepository.save(existingJob);
        redirectAttributes.addFlashAttribute("successMessage", "Job ID " + existingJob.getId() + " updated successfully.");
        return "redirect:/admin/dashboard";
    }


    /**
     * Handles the Admin User Management dashboard: shows all users.
     * URL: /admin/users
     */
    @GetMapping("/users")
    @Transactional(readOnly = true)
    public String manageUsers(Model model) {
        List<User> allUsers = userRepository.findAll();
        List<Role> allRoles = roleRepository.findAll();

        model.addAttribute("users", allUsers);
        model.addAttribute("allRoles", allRoles);
        return "admin/user_management";
    }

    /**
     * Handles the POST request to update a user's role and status.
     * URL: /admin/user/update
     */
    @PostMapping("/users/update") // Corrected path to match form
    @Transactional
    public String updateUser(@RequestParam("userId") Long userId,
                             @RequestParam("roleId") Long roleId,
                             @RequestParam(value = "isEnabled", required = false) Boolean isEnabled,
                             RedirectAttributes redirectAttributes) {

        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Role> roleOpt = roleRepository.findById(roleId);

        if (userOpt.isEmpty() || roleOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: User or Role not found.");
            return "redirect:/admin/users";
        }

        User user = userOpt.get();
        Role newRole = roleOpt.get();

        user.setRole(newRole);
        user.setIsEnabled(isEnabled != null && isEnabled);

        if (newRole.getName().equals("EMPLOYER")) {
            user.setSeekerProfile(null);
        } else if (newRole.getName().equals("JOB_SEEKER")) {
            user.setCompanyName(null);
            user.setCompanyDescription(null);
            user.setCompanyLocation(null);
            user.setCompanyLogoFilename(null);
        }

        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage", "User " + user.getUsername() + " updated successfully.");
        return "redirect:/admin/users";
    }

    // **** START OF CHAT METHODS ****

    /**
     * GET: Shows the main admin support inbox page.
     */
    @GetMapping("/support")
    @Transactional(readOnly = true)
    public String showSupportInbox(Model model, @AuthenticationPrincipal UserDetails userDetails) {

        List<User> allUsers = userRepository.findAll();

        List<User> seekers = allUsers.stream()
                .filter(u -> u.getRole() != null && "JOB_SEEKER".equals(u.getRole().getName()))
                .collect(Collectors.toList());

        List<User> employers = allUsers.stream()
                .filter(u -> u.getRole() != null && "EMPLOYER".equals(u.getRole().getName()))
                .collect(Collectors.toList());

        model.addAttribute("seekers", seekers);
        model.addAttribute("employers", employers);
        model.addAttribute("adminUser", userRepository.findByUsername(userDetails.getUsername()).orElse(null));

        return "admin/support_inbox";
    }

    /**
     * API GET: Fetches the chat history for a specific user.
     */
    @GetMapping("/api/support/messages/{userId}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getSupportMessages(@PathVariable Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        String chatLogJson = "[]";
        try {
            if ("JOB_SEEKER".equals(user.getRole().getName())) {

                Optional<SeekerProfile> profileOpt = seekerProfileRepository.findByUser(user);
                if (profileOpt.isPresent()) {
                    chatLogJson = profileOpt.get().getSupportChatLog() != null ? profileOpt.get().getSupportChatLog() : "[]";
                }

            } else if ("EMPLOYER".equals(user.getRole().getName())) {
                chatLogJson = user.getAdminSupportChatLog() != null ? user.getAdminSupportChatLog() : "[]";
            }

            List<Map<String, Object>> messages = objectMapper.readValue(chatLogJson, new TypeReference<List<Map<String, Object>>>() {});
            return ResponseEntity.ok(messages);

        } catch (Exception e) {
            return ResponseEntity.ok(new ArrayList<>()); // Return empty on error
        }
    }

    /**
     * API POST: Sends a reply from the Admin to a specific user.
     */
    @PostMapping("/api/support/reply/{userId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> sendSupportReply(@PathVariable Long userId,
                                                   @RequestBody Map<String, String> payload,
                                                   @AuthenticationPrincipal UserDetails userDetails) {

        User userToReplyTo = userRepository.findById(userId).orElse(null);
        User adminUser = userRepository.findByUsername(userDetails.getUsername()).orElse(null);

        if (userToReplyTo == null || adminUser == null) {
            return ResponseEntity.notFound().build();
        }

        String message = payload.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Message cannot be empty.");
        }

        try {
            Map<String, Object> newNote = new HashMap<>();
            newNote.put("senderId", adminUser.getId());
            newNote.put("senderFirstName", "Admin");
            newNote.put("senderLastName", "Support");
            newNote.put("message", message);
            newNote.put("createdAt", LocalDate.now().toString());
            newNote.put("senderRole", "ADMIN");

            // Save to the correct log based on user role
            if ("JOB_SEEKER".equals(userToReplyTo.getRole().getName())) {

                SeekerProfile profile = seekerProfileRepository.findByUser(userToReplyTo)
                        .orElse(new SeekerProfile(userToReplyTo));

                String chatLogJson = profile.getSupportChatLog() != null ? profile.getSupportChatLog() : "[]";
                List<Map<String, Object>> messages = objectMapper.readValue(chatLogJson, new TypeReference<List<Map<String, Object>>>() {});
                messages.add(newNote);
                profile.setSupportChatLog(objectMapper.writeValueAsString(messages));
                seekerProfileRepository.save(profile);

            } else if ("EMPLOYER".equals(userToReplyTo.getRole().getName())) {
                String chatLogJson = userToReplyTo.getAdminSupportChatLog() != null ? userToReplyTo.getAdminSupportChatLog() : "[]";
                List<Map<String, Object>> messages = objectMapper.readValue(chatLogJson, new TypeReference<List<Map<String, Object>>>() {});
                messages.add(newNote);
                userToReplyTo.setAdminSupportChatLog(objectMapper.writeValueAsString(messages));
                userRepository.save(userToReplyTo);
            }

            return ResponseEntity.ok("{\"status\": \"success\"}");

        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"status\": \"error\"}");
        }
    }
}