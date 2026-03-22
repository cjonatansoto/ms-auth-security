package com.rocketpj.application.service;

import com.rocketpj.application.entity.AuditLogEntity;
import com.rocketpj.application.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(UUID userId, String action, String description, String ipAddress, String userAgent) {
        try {
            var entry = AuditLogEntity.builder()
                    .userId(userId)
                    .action(action)
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist audit log: action={}, userId={}", action, userId, e);
        }
    }
}
