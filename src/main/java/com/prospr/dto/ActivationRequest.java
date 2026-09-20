package com.prospr.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivationRequest(@NotBlank String token, @NotBlank String password) { }
