package com.example.jobrecruitmentsystem.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Collection;

@Component
public class AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String targetUrl = determineTargetUrl(authorities);

        if (response.isCommitted()) {
            return;
        }
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(Collection<? extends GrantedAuthority> authorities) {
        // Redirect all valid roles to the Index/Home page ("/")
        // The index.html template handles showing the correct view (Admin/Employer/Seeker)
        if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ADMIN")) ||
                authorities.stream().anyMatch(a -> a.getAuthority().equals("EMPLOYER")) ||
                authorities.stream().anyMatch(a -> a.getAuthority().equals("JOB_SEEKER"))) {
            return "/";
        } else {
            // Fallback for users with no valid role
            return "/login?error=true";
        }
    }
}