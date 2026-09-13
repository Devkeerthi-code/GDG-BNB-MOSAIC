package com.hruthikesh.ime.dto;

import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NegotiationStatusResponse {
    private NegotiationStatus status;
    private Integer currentRound;
    private Integer maxRounds;
    private List<NegotiationTurnDto> turns;
    private boolean reportAvailable;
    private String failureReason;
}
