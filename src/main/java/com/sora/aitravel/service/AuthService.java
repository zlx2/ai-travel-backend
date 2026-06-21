package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.LoginRequest;
import com.sora.aitravel.dto.request.RegisterRequest;
import com.sora.aitravel.dto.response.LoginResponse;

public interface AuthService {
    Long register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout();
}
