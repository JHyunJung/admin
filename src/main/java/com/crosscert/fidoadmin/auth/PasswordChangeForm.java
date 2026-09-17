package com.crosscert.fidoadmin.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PasswordChangeForm {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min = 8, max = 64)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$", message = "영문, 숫자, 특수문자를 모두 포함해야 합니다.")
    private String newPassword;
    @NotBlank private String confirmPassword;
}
