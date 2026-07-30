package com.urlshortener.service;

import com.urlshortener.dto.request.LoginRequest;
import com.urlshortener.dto.request.RefreshTokenRequest;
import com.urlshortener.dto.request.RegisterRequest;
import com.urlshortener.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);
}
