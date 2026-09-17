package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_SYSTEM_PROP — 고객사별 key-value. PK 없음. ERD 컬럼 5개. */
@Entity
@Table(name = "CCFA_SYSTEM_PROP")
@Getter @Setter @NoArgsConstructor
public class CcfaSystemProp {
    @EmbeddedId private CcfaSystemPropId id;
    @Column(name = "PROP_VALUE", length = 4000) private String propValue;
    @Column(name = "SHARE_TYPE", length = 20) private String shareType;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
