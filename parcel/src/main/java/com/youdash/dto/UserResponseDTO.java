package com.youdash.dto;

import lombok.Data;

@Data
public class UserResponseDTO {

    private Long id;

    private String phoneNumber;

    private String firstName;
    private String lastName;

    private String email;

    private Boolean active;
    private Boolean profileCompleted;

    private String token;
}