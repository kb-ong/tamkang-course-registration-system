package edu.tku.registration.service;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public record RegistrationPeriodInfo(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endAt,
        boolean open) { }
