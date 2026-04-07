package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_package_categories")
@Data
public class PackageCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "default_description")
    private String defaultDescription;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "image_url")
    private String imageUrl;
}
