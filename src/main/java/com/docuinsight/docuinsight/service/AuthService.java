package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.AuthResponse;
import com.docuinsight.docuinsight.model.RegisterRequest;
import com.docuinsight.docuinsight.model.User;
import com.docuinsight.docuinsight.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest request)
    {
        //step 1 check if email already exists
        if(userRepository.existsByEmail(request.getEmail()))
        {
            throw new RuntimeException("Email already registered");
        }
        //step 2 Hash the password
        String hashedPassword =passwordEncoder.encode(request.getPassword());
        //step 3 build user object
        User user= User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(hashedPassword)
                .build();
        //step 4 Save to DB
        userRepository.save(user);
        //Step 5 return success response
        return new AuthResponse("Registration Successful",null);
    }
}
