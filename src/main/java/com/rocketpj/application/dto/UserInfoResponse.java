package com.rocketpj.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserInfoResponse(
        UUID id,
        String email,
        String name,
        String lastname,
        String phone,
        Set<String> roles
) {}
