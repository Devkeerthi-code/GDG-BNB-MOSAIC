package com.hruthikesh.ime.dto;

import com.hruthikesh.ime.entity.enums.AgentRole;
import com.hruthikesh.ime.entity.enums.TurnDecision;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class NegotiationTurnDto {
    private Long id;
    private Integer roundNumber;
    private AgentRole agentRole;
    private String speakingForOrgName;
    private String message;
    private BigDecimal proposedPricePerUnit;
    private BigDecimal proposedQuantity;
    private TurnDecision decision;
    private LocalDateTime createdAt;
}
