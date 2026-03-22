package com.rocketpj.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "{auth.email.required}")
        @Email(message = "{auth.email.invalid}")
        String email,

        @NotBlank(message = "{auth.password.required}")
        @Size(min = 8, max = 32, message = "{auth.password.size}")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).+$",
                message = "{auth.password.too_weak}"
        )
        String password,

        @NotBlank(message = "Name is required")
        String name,

        String lastname,

        String phone
) {}
