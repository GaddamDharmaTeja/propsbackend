package com.prospr.dto;
import jakarta.validation.constraints.NotBlank;
public record LoginRequest(@NotBlank String identity, @NotBlank String password) {}
