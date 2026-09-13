package com.hruthikesh.ime.entity;

import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import com.hruthikesh.ime.entity.enums.Unit;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "negotiations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE negotiations SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Negotiation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supply_id", nullable = false)
    private Supply supply;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    private Demand demand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_org_id", nullable = false)
    private Organisation supplierOrg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demander_org_id", nullable = false)
    private Organisation demanderOrg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by_org_id", nullable = false)
    private Organisation initiatedByOrg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NegotiationStatus status = NegotiationStatus.PENDING;

    private Double matchScoreAtStart;

    @Builder.Default
    private Integer maxRounds = 6;

    @Builder.Default
    private Integer currentRound = 0;

    private BigDecimal agreedPricePerUnit;
    private BigDecimal agreedQuantity;

    @Enumerated(EnumType.STRING)
    private Unit agreedUnit;

    @Column(columnDefinition = "TEXT")
    private String agreedTerms;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    private LocalDateTime completedAt;
}
