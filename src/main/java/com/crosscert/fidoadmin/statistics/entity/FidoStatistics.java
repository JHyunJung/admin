package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FIDO_STATISTICS — PK 없음. ERD 컬럼 12개. */
@Entity
@Table(name = "FIDO_STATISTICS")
@Getter @Setter @NoArgsConstructor
public class FidoStatistics {
    @EmbeddedId private FidoStatisticsId id;
    @Column(name = "AUTH_S") private Long authS;
    @Column(name = "AUTH_F") private Long authF;
    @Column(name = "TC_S") private Long tcS;
    @Column(name = "TC_F") private Long tcF;
    @Column(name = "REG_S") private Long regS;
    @Column(name = "REG_F") private Long regF;
    @Column(name = "DEREG_S") private Long deregS;
    @Column(name = "DEREG_F") private Long deregF;
}
