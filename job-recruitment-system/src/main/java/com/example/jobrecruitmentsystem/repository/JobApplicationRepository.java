package com.example.jobrecruitmentsystem.repository;

import com.example.jobrecruitmentsystem.model.JobApplication;
import com.example.jobrecruitmentsystem.model.JobRequirement;
import com.example.jobrecruitmentsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findBySeekerAndJob(User seeker, JobRequirement job);
    List<JobApplication> findBySeeker(User seeker);

    // This method resolves the Lazy Loading error in the Seeker Dashboard.
    @Query("SELECT a FROM JobApplication a JOIN FETCH a.job j JOIN FETCH j.postedBy pb WHERE a.seeker = :seeker")
    List<JobApplication> findBySeekerEagerly(@Param("seeker") User seeker);

    // --- CRITICAL FIX FOR COMPILATION FAILURE (Invalid Path) ---
    // Corrected Query: Changed 'j.job.postedBy' to the correct 'j.postedBy'
    @Query("SELECT a FROM JobApplication a JOIN FETCH a.job j JOIN FETCH j.postedBy pb WHERE a.id = :id")
    Optional<JobApplication> findApplicationWithDetailsById(@Param("id") Long id);

    // This method is required by EmployerController (fixed in the previous step's logic)
    List<JobApplication> findByJob(JobRequirement job);
}