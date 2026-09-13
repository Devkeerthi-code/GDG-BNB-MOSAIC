package com.hruthikesh.ime.entity;

import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "negotiation_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negotiation_id", nullable = false)
    private Negotiation negotiation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "for_org_id", nullable = false)
    private Organisation forOrg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterpart_org_id", nullable = false)
    private Organisation counterpartOrg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NegotiationStatus outcome;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String recommendation;

    @Column(columnDefinition = "TEXT")
    private String dealTerms;

    @Column(nullable = false)
    private LocalDateTime generatedAt;
}
