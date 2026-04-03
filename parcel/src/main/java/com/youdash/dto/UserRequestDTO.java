package com.youdash.dto;

import lombok.Data;

@Data
public class UserRequestDTO {

    private String phoneNumber;

    private String firstName;
    private String lastName;

    private String email;
}