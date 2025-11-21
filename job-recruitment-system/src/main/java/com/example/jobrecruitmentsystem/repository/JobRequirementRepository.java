package com.example.jobrecruitmentsystem.repository;

import com.example.jobrecruitmentsystem.model.JobRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {
    long countByStatus(String status);
    List<JobRequirement> findByStatus(String status);
    List<JobRequirement> findByStatusAndTitleContainingIgnoreCase(String status, String keyword);

    // --- NEW: Efficiently count jobs for a specific user with specific statuses ---
    long countByPostedBy_IdAndStatusIn(Long userId, List<String> statuses);

    // UPDATED QUERY: This query ensures featured jobs appear first (j.isFeatured DESC)
    @Query("SELECT j FROM JobRequirement j WHERE " +
            "j.status = :status AND " +
            "(:keyword IS NULL OR (LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(j.postedBy.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))) AND " +
            "(:location IS NULL OR LOWER(j.locationCity) LIKE LOWER(CONCAT('%', :location, '%'))) AND " +
            "(:category IS NULL OR :category = '' OR j.jobCategory = :category) AND " +
            "(:jobTypes IS NULL OR j.jobType IN :jobTypes) " +
            "ORDER BY j.isFeatured DESC, j.postedAt DESC")
    List<JobRequirement> findJobsByCriteria(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("location") String location,
            @Param("category") String category,
            @Param("jobTypes") List<String> jobTypes
    );
}