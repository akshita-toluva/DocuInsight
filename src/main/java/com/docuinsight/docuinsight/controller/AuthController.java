/***************Receives requests from Postman *********************/
package com.docuinsight.docuinsight.controller;

import com.docuinsight.docuinsight.model.AuthResponse;
import com.docuinsight.docuinsight.model.LoginRequest;
import com.docuinsight.docuinsight.model.RegisterRequest;
import com.docuinsight.docuinsight.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request){
        AuthResponse response=authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request){
        AuthResponse response=authService.login(request);
        return ResponseEntity.ok(response);
    }
}
