package com.example.jobrecruitmentsystem.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public final class AppConstants {

    private AppConstants() {}

    public static final int PROFILE_COMPLETION_THRESHOLD = 70;

    // --- NEWLY ADDED CONSTANTS FOR DPU JOB ---
    public static final String JOB_TYPE_FULL_TIME = "FULL-TIME";
    public static final String JOB_CATEGORY_EDUCATION = "Education";

    // --- PREMIUM TIERS ---
    public static final String TIER_BASIC = "BASIC";
    public static final String TIER_PREMIUM = "PREMIUM";
    public static final String TIER_PRO = "PRO";

    public static final List<String> ALL_JOB_TYPES = List.of(
            "FULL-TIME",
            "PART-TIME",
            "REMOTE",
            "FREELANCE",
            "CONTRACT",
            "TEMPORARY"
    );

    public static final List<String> ALL_JOB_CATEGORIES = Arrays.asList(
            "Design & Creative",
            "Design & Development",
            "Sales & Marketing",
            "Mobile Application",
            "Construction",
            "Information Technology",
            "Real Estate",
            "Content Writer",
            "Digital Marketing",
            "Software Development",
            "Human Resources",
            "Finance",
            "Sales",
            "Education" // Added for DPU
    );

    public static final List<String> PROMINENT_SKILLS = Arrays.asList(
            "Java",
            "Python",
            "JavaScript",
            "React",
            "SQL",
            "AWS",
            "Cloud Computing",
            "Project Management",
            "Data Analysis",
            "Machine Learning",
            "Native English Fluency", // <-- Added for DPU match
            "TESOL/TEFL",             // <-- Added for DPU match
            "Curriculum Development", // <-- Added for DPU match
            "Classroom Management"    // <-- Added for DPU match
    );

    public static final Map<String, Integer> MOCK_SKILL_COUNTS = Map.of(
            "Java", 433,
            "Python", 554,
            "JavaScript", 374,
            "React", 321,
            "SQL", 621,
            "AWS", 470,
            "Cloud Computing", 511,
            "Project Management", 586,
            "Data Analysis", 692,
            "Machine Learning", 599
            // Teaching skills are implicitly included in the random pool
    );

    public static final Map<String, String> SECURITY_QUESTIONS = Map.of(
            "pet", "What is the name of your first pet?",
            "car", "What was your first car?",
            "maiden_name", "What is your mother's maiden name?",
            "high_school", "What high school did you attend?",
            "first_school", "What is the name of your first school?",
            "birth_city", "In what city were you born?",
            "movie", "What is your favorite movie?",
            "food", "What is your favorite food?",
            "best_friend", "What is the name of your childhood best friend?",
            "street", "What street did you grow up on?"
    );
}