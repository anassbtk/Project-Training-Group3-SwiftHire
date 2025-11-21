package com.example.jobrecruitmentsystem.service;

import com.example.jobrecruitmentsystem.config.AiAssistantConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiAssistantService {

    private final RestTemplate restTemplate;
    private final AiAssistantConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiAssistantService(RestTemplate restTemplate, AiAssistantConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    // Existing method for Seeker Chat
    public String getAiResponse(String userPrompt) {
        return callOpenRouter(userPrompt, "You are a helpful job assistant for the SwiftHire platform.");
    }

    // Existing Method: Context-Aware Job Description
    public String generateJobDescription(String jobTitle, String location, String jobType, String salaryRange) {
        String userPrompt = String.format(
                "Write a professional job description for a '%s' position.",
                jobTitle
        );
        if (location != null && !location.isBlank()) {
            userPrompt += " The job is located in " + location + ".";
        }
        if (jobType != null && !jobType.isBlank()) {
            userPrompt += " It is a " + jobType + " role.";
        }
        if (salaryRange != null && !salaryRange.isBlank()) {
            userPrompt += " The offered salary range is " + salaryRange + ".";
        }

        String systemPrompt = "You are an expert HR assistant. Write a compelling, professional job description. " +
                "Structure it with these Markdown headings: 'About the Role', 'Key Responsibilities', and 'Requirements'. " +
                "Incorporating the location and salary benefits into the text where appropriate. " +
                "Keep it under 2000 characters.";

        return callOpenRouter(userPrompt, systemPrompt);
    }

    // Existing Method: Match Score
    public Map<String, Object> analyzeCandidateMatch(String jobDescription, String candidateProfile) {
        String systemPrompt = "You are an expert ATS. Compare the candidate profile to the job description. " +
                "Return valid JSON only (no markdown) with keys: " +
                "\"score\" (integer 0-100), " +
                "\"reasoning\" (concise 1-sentence summary), " +
                "\"missingKeywords\" (array of strings, max 3).";

        String userPrompt = "JOB DESCRIPTION:\n" + (jobDescription.length() > 1500 ? jobDescription.substring(0, 1500) : jobDescription) +
                "\n\nCANDIDATE PROFILE:\n" + candidateProfile;

        String jsonResponse = callOpenRouter(userPrompt, systemPrompt);

        try {
            jsonResponse = jsonResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("score", 0);
            fallback.put("reasoning", "AI analysis unavailable.");
            fallback.put("missingKeywords", List.of());
            return fallback;
        }
    }

    // --- NEW METHOD: Candidate Analyzer (For Employers) ---
    public Map<String, Object> generateCandidateAnalysis(String candidateProfile) {
        String systemPrompt = "You are a Senior Technical Recruiter. Analyze this candidate profile. " +
                "Return valid JSON only (no markdown) with these keys: " +
                "\"summary\" (string, 1 professional sentence about their level), " +
                "\"strengths\" (array of strings, top 3 hard/soft skills), " +
                "\"questions\" (array of strings, 3 tailored interview questions to ask them).";

        String userPrompt = "CANDIDATE PROFILE:\n" + candidateProfile;

        String jsonResponse = callOpenRouter(userPrompt, systemPrompt);

        try {
            jsonResponse = jsonResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("summary", "Analysis unavailable.");
            fallback.put("strengths", List.of("N/A"));
            fallback.put("questions", List.of("Could not generate questions."));
            return fallback;
        }
    }

    // Helper method
    private String callOpenRouter(String userPrompt, String systemPrompt) {
        String url = config.getApiUrl();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "SwiftHire");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        Map<String, Object> sysMessage = new HashMap<>();
        sysMessage.put("role", "system");
        sysMessage.put("content", systemPrompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek/deepseek-chat");
        requestBody.put("messages", List.of(sysMessage, userMessage));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
                if (responseMap.containsKey("choices")) {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        if (message != null && message.containsKey("content")) {
                            return (String) message.get("content");
                        }
                    }
                }
                return "AI Error: Could not parse response.";
            } else {
                return "API Error: OpenRouter returned status " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Network Error: " + e.getMessage();
        }
    }
}