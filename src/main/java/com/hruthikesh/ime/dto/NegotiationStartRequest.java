package com.hruthikesh.ime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NegotiationStartRequest {
    private Long supplyId;
    private Long demandId;
}
