package com.smartdoor.backend.control.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnlockCommandPayload {
    private String commandId;
    private String action; // ví dụ: "UNLOCK"
    private long timestamp; // Unix timestamp
}