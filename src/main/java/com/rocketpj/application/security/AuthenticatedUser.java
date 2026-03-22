package com.rocketpj.application.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, String jti) {}
