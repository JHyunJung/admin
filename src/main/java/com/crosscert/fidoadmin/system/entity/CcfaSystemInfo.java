package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_SYSTEM_INFO — key-value. ERD 컬럼 3개. */
@Entity
@Table(name = "CCFA_SYSTEM_INFO")
@Getter @Setter @NoArgsConstructor
public class CcfaSystemInfo {
    @Id @Column(name = "PROP_KEY", length = 128) private String propKey;
    @Column(name = "PROP_VALUE", length = 1024) private String propValue;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
