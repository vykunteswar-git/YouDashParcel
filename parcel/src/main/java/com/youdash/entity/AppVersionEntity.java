package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_app_version")
@Data
public class AppVersionEntity {

    @Id
    private Long id;

    @Column(name = "user_version_code")
    private Integer userVersionCode;

    @Column(name = "user_play_store_url", length = 512)
    private String userPlayStoreUrl;

    @Column(name = "rider_version_code")
    private Integer riderVersionCode;

    @Column(name = "rider_play_store_url", length = 512)
    private String riderPlayStoreUrl;
}
