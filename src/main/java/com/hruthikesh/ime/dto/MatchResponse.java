package com.hruthikesh.ime.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class MatchResponse {
    private Long id;
    private String name;
    private String description;
    private String orgName;
    private String orgContact;
    private BigDecimal pricePerUnit;
    private BigDecimal quantity;
    private String unit;
    private double distanceKm;


    private Double latitude;
    private Double longitude;


    private double textScore;
    private double priceScore;
    private double distanceScore;
    private double quantityScore;
    private double totalScore;
}
