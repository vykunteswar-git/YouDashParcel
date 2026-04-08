package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_admins")
@Data
public class AdminEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
