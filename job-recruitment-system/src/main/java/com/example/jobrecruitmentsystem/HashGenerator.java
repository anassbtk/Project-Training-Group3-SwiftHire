package com.example.jobrecruitmentsystem;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("adminpass");
        System.out.println("Admin Hash: " + hash);
    }
}
