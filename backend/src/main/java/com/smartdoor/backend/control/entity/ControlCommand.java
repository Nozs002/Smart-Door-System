package com.smartdoor.backend.control.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="control_commands") @Getter @Setter
public class ControlCommand {
    @Id @Column(length=36) private String id;
    @Column(nullable=false) private Long doorId;
    @Column(nullable=false) private Long accountId;
    @Column(nullable=false,length=100) private String deviceCode;
    @Column(nullable=false,length=16) private String status;
    @Column(nullable=false) private LocalDateTime createdAt;
    @Column(nullable=false) private LocalDateTime expiresAt;
}
