package com.hruthikesh.ime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Contact number is required")
    @Size(max = 15, message = "Contact number cannot exceed 15 characters")
    private String contactNumber;

    @NotBlank(message = "Password is required")
    private String password;
}
