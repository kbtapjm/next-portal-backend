package com.nextportal.common.authorization.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portal_permission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission {
    @Getter
    @Id @Column(length = 80)
    private String code;
    @Getter
    @Column(nullable = false, length = 200)
    private String description;
}
