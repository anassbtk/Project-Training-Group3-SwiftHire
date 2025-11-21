package com.example.jobrecruitmentsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileService {

    private final Path rootUploadDir;
    private final Path companyLogosDir;
    private final Path resumesDir;
    private final Path profilePicsDir;

    public static final String COMPANY_LOGO_SUBDIR = "company_logos";
    public static final String RESUME_SUBDIR = "resumes";
    public static final String PROFILE_PIC_SUBDIR = "profile_pics";

    public FileService(@Value("${file.upload-dir}") String uploadDir) throws IOException {
        this.rootUploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.companyLogosDir = rootUploadDir.resolve(COMPANY_LOGO_SUBDIR);
        this.resumesDir = rootUploadDir.resolve(RESUME_SUBDIR);
        this.profilePicsDir = rootUploadDir.resolve(PROFILE_PIC_SUBDIR);

        try {
            Files.createDirectories(rootUploadDir);
            Files.createDirectories(companyLogosDir);
            Files.createDirectories(resumesDir);
            Files.createDirectories(profilePicsDir);
        } catch (IOException e) {
            throw new IOException("Could not create upload directories!", e);
        }
    }

    public String saveCompanyLogoFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return null;
        }
        return saveFile(file, companyLogosDir, null);
    }

    public String saveResumeFile(MultipartFile file, Long userId) throws IOException {
        if (file.isEmpty()) {
            return null;
        }
        String prefix = "user_" + userId + "_";
        return saveFile(file, resumesDir, prefix);
    }

    // This method is crucial for saving the profile pictures
    public String saveProfilePictureFile(MultipartFile file, Long userId) throws IOException {
        if (file.isEmpty()) {
            return null;
        }
        String prefix = "user_pic_" + userId + "_";
        return saveFile(file, profilePicsDir, prefix);
    }


    private String saveFile(MultipartFile file, Path directory, String prefix) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Generate a unique filename using UUID (This is what most controller/DB code expects)
        String uniqueFileName = (prefix != null ? prefix : "") + UUID.randomUUID().toString() + fileExtension;

        Path filePath = directory.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return uniqueFileName;
    }

    public Resource loadFileAsResource(String subdirectory, String filename) throws MalformedURLException {
        Path directory;

        if (RESUME_SUBDIR.equals(subdirectory)) {
            directory = this.resumesDir;
        } else if (COMPANY_LOGO_SUBDIR.equals(subdirectory)) {
            directory = this.companyLogosDir;
        } else if (PROFILE_PIC_SUBDIR.equals(subdirectory)) {
            directory = this.profilePicsDir;
        } else {
            directory = this.rootUploadDir;
        }

        // Critically, for mock data, the filename in the DB is the final name.
        Path filePath = directory.resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new MalformedURLException("File not found or not readable: " + filename);
        }
    }
}