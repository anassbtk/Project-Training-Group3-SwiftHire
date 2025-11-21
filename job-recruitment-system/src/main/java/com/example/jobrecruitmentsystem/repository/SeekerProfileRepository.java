package com.example.jobrecruitmentsystem.repository;

import com.example.jobrecruitmentsystem.model.SeekerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import com.example.jobrecruitmentsystem.model.User;

@Repository
public interface SeekerProfileRepository extends JpaRepository<SeekerProfile, Long> {

    Optional<SeekerProfile> findByUser(User user);

    List<SeekerProfile> findByOfferingServicesTrue();

    // --- UPDATED: Filter by TITLE instead of CATEGORY for better matching ---
    @Query("SELECT p FROM SeekerProfile p JOIN FETCH p.user u " +
            "WHERE (:skills IS NULL OR :skills = '' OR LOWER(p.skills) LIKE LOWER(CONCAT('%', :skills, '%'))) " +
            "AND (:city IS NULL OR :city = '' OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%'))) " +
            "AND (:title IS NULL OR :title = '' OR LOWER(p.currentTitle) LIKE LOWER(CONCAT('%', :title, '%'))) " +
            "ORDER BY p.isFeatured DESC, p.id DESC")
    List<SeekerProfile> findFilteredCandidates(
            @Param("skills") String skills,
            @Param("city") String city,
            @Param("title") String title
    );
}