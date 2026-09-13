package com.hruthikesh.ime.dto;

import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NegotiationReportDto {
    private Long negotiationId;
    private String forOrgName;
    private String counterpartOrgName;
    private NegotiationStatus outcome;
    private String summary;
    private String recommendation;
    private String dealTerms;
    private LocalDateTime generatedAt;
}
