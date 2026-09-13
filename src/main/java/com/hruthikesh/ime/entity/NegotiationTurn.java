package com.hruthikesh.ime.entity;

import com.hruthikesh.ime.entity.enums.AgentRole;
import com.hruthikesh.ime.entity.enums.TurnDecision;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "negotiation_turns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negotiation_id", nullable = false)
    private Negotiation negotiation;

    @Column(nullable = false)
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentRole agentRole;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "speaking_for_org_id", nullable = false)
    private Organisation speakingForOrg;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    private BigDecimal proposedPricePerUnit;
    private BigDecimal proposedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TurnDecision decision;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
