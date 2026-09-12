package com.docuinsight.docuinsight.security;

import com.docuinsight.docuinsight.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader=request.getHeader("Authorization");
        if(authHeader==null || !authHeader.startsWith("Bearer "))
        {
            filterChain.doFilter(request,response);
            return;
        }
        String token=authHeader.substring(7);
        if(jwtService.isTokenValid(token))
        {
            String email=jwtService.extractEmail(token);
            var userOpt=userRepository.findByEmail(email);
            if(userOpt.isPresent())
            {
                var auth=new UsernamePasswordAuthenticationToken(email,null,new ArrayList<>());
                SecurityContextHolder.getContext()
                        .setAuthentication(auth);
            }
        }
        filterChain.doFilter(request,response);
    }
}
