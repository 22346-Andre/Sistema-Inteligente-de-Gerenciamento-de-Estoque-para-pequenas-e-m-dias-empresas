package com.smartstock.backend.dto;

public record LoginResponse(String accessToken, Long expiresIn) {
}