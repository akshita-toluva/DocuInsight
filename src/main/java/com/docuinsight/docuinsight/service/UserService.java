package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.*;
import com.docuinsight.docuinsight.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileResponse getProfile(String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new ResponseStatusException(
                        HttpStatus.NOT_FOUND,"User Not Found"));

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt());
    }

    @Transactional
    public UserProfileResponse updateProfile(String email,UpdateProfileRequest request)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new ResponseStatusException(
                        HttpStatus.NOT_FOUND,"User Not Found"));

        user.setName(request.getName().trim());
        userRepository.save(user);

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt());
    }

    @Transactional
    public void changePassword(String email,ChangePasswordRequest request)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new ResponseStatusException(
                        HttpStatus.NOT_FOUND,"User Not Found"));

        //Case-1 Current password is wrong
        if(!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword()))
        {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }

        //Case-2 New password same as old password
        if(passwordEncoder.matches(request.getNewPassword(), user.getPassword()))
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }

        //All check passed
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

}
