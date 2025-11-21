package com.example.jobrecruitmentsystem.controller;

import com.example.jobrecruitmentsystem.model.JobRequirement;
import com.example.jobrecruitmentsystem.model.SeekerProfile;
import com.example.jobrecruitmentsystem.model.User;
import com.example.jobrecruitmentsystem.repository.JobRequirementRepository;
import com.example.jobrecruitmentsystem.repository.SeekerProfileRepository;
import com.example.jobrecruitmentsystem.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/premium")
public class PremiumController {

    private final UserService userService;
    private final JobRequirementRepository jobRepository;
    private final SeekerProfileRepository seekerProfileRepository; // NEW: Injected Repository

    public PremiumController(UserService userService,
                             JobRequirementRepository jobRepository,
                             SeekerProfileRepository seekerProfileRepository) {
        this.userService = userService;
        this.jobRepository = jobRepository;
        this.seekerProfileRepository = seekerProfileRepository;
    }

    // Tier constants for mapping
    private static final String TIER_PREMIUM = "PREMIUM";
    private static final String TIER_PRO = "PRO";
    private static final double PRICE_PREMIUM = 9.99;
    private static final double PRICE_PRO = 49.99;

    /**
     * Shows the premium pricing page (employer_premium_page.html).
     * Handles authentication manually since it's a permitAll endpoint.
     */
    @GetMapping
    public String showPremiumPlans(Model model) {

        // Use Authentication context to safely check login status
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = null;

        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof UserDetails) {
            username = authentication.getName();
        }

        model.addAttribute("tierBasic", "BASIC");
        model.addAttribute("tierPremium", TIER_PREMIUM);
        model.addAttribute("tierPro", TIER_PRO);

        if (username != null && !username.equals("anonymousUser")) {
            User user = userService.findByUsername(username);
            if (user != null && user.getPremiumTier() != null) {
                model.addAttribute("currentTier", user.getPremiumTier());
            }
        }

        return "employer_premium_page";
    }

    /**
     * Handles the POST request from the pricing page to initiate checkout.
     * Maps to premium_checkout.html.
     */
    @PostMapping("/checkout")
    public String startCheckout(@RequestParam("tier") String tier,
                                Model model,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes redirectAttributes) {

        if (userDetails == null || userDetails.getUsername().equals("anonymousUser")) {
            redirectAttributes.addFlashAttribute("errorMessage", "You must be logged in to upgrade your plan.");
            return "redirect:/login";
        }

        User user = userService.findByUsername(userDetails.getUsername());

        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User account not found.");
            return "redirect:/";
        }

        double amount;
        String description;

        if (TIER_PREMIUM.equals(tier)) {
            amount = PRICE_PREMIUM;
            description = "SwiftHire Premium Subscription (Monthly)";
        } else if (TIER_PRO.equals(tier)) {
            amount = PRICE_PRO;
            description = "SwiftHire Pro Subscription (Monthly)";
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid subscription tier selected.");
            return "redirect:/premium";
        }

        String paymentId = UUID.randomUUID().toString();

        model.addAttribute("tier", tier);
        model.addAttribute("amount", amount);
        model.addAttribute("description", description);
        model.addAttribute("paymentId", paymentId);

        return "premium_checkout";
    }

    /**
     * Handles the completion of the mock payment (success handler).
     * Updates user tier, releases held jobs (employers), and sets featured status (seekers).
     */
    @GetMapping("/complete")
    @Transactional // Ensure DB consistency
    public String completeUpgrade(@RequestParam("paymentId") String paymentId,
                                  @RequestParam("tier") String tier,
                                  @AuthenticationPrincipal UserDetails userDetails,
                                  RedirectAttributes redirectAttributes) {

        if (userDetails == null || userDetails.getUsername().equals("anonymousUser")) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(userDetails.getUsername());

        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User account not found.");
            return "redirect:/";
        }

        System.out.println("Mock Payment Received. ID: " + paymentId + ", Tier: " + tier + ", User: " + user.getUsername());

        // 1. Update User Tier
        user.setPremiumTier(tier);
        userService.saveUser(user);

        // 2. Employer Logic: Release "ON_HOLD" jobs
        if ("EMPLOYER".equals(user.getRole().getName())) {
            List<JobRequirement> heldJobs = jobRepository.findByStatus("ON_HOLD");
            int releasedCount = 0;

            for (JobRequirement job : heldJobs) {
                // Only release jobs belonging to this user
                if (job.getPostedBy().getId().equals(user.getId())) {
                    job.setStatus("PENDING_ADMIN"); // Move to Admin Queue
                    jobRepository.save(job);
                    releasedCount++;
                }
            }

            if (releasedCount > 0) {
                redirectAttributes.addFlashAttribute("infoMessage",
                        releasedCount + " previously held job(s) have been submitted for Admin approval.");
            }
        }

        // 3. NEW: Job Seeker Logic: Activate "Featured Profile"
        if ("JOB_SEEKER".equals(user.getRole().getName())) {
            Optional<SeekerProfile> profileOpt = seekerProfileRepository.findByUser(user);
            if (profileOpt.isPresent()) {
                SeekerProfile profile = profileOpt.get();
                profile.setIsFeatured(true);
                seekerProfileRepository.save(profile);
            }
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Congratulations! Your account has been successfully upgraded to the **" + tier + "** tier."
        );

        // Redirect based on User Role
        String role = user.getRole().getName();
        if ("JOB_SEEKER".equals(role)) {
            return "redirect:/seeker/dashboard?view=dashboard";
        } else if ("EMPLOYER".equals(role)) {
            return "redirect:/employer/dashboard?view=overview";
        } else {
            return "redirect:/";
        }
    }
}