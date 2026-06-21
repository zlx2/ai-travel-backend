package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailCodeRequest(@NotBlank @Email String email, @NotBlank String scene) {}
