package com.hruthikesh.ime.service;

import com.hruthikesh.ime.dto.MatchResponse;
import com.hruthikesh.ime.entity.Demand;
import com.hruthikesh.ime.entity.Supply;
import com.hruthikesh.ime.entity.enums.Status;
import com.hruthikesh.ime.entity.enums.Unit;
import com.hruthikesh.ime.repository.DemandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.hruthikesh.ime.repository.SupplyRepository;

@Service
public class MatchingAlgorithmService {

    private final DemandRepository demandRepository;
    private final SupplyRepository supplyRepository;

    @Autowired
    public MatchingAlgorithmService(DemandRepository demandRepository, SupplyRepository supplyRepository) {
        this.demandRepository = demandRepository;
        this.supplyRepository = supplyRepository;
    }

    public MatchResponse computeMatchScore(Supply supply, Demand demand) {
        return calculateScore(supply, demand, true);
    }

    public List<MatchResponse> discoverMatchesForSupply(Supply supply) {
        List<Unit> compatibleUnits = getCompatibleUnits(supply.getUnit());

        List<Demand> candidateDemands = demandRepository.findByStatusAndCategoryIdAndUnitIn(
                Status.ACTIVE, supply.getCategory().getId(), compatibleUnits);

        List<MatchResponse> results = new ArrayList<>();

        for (Demand demand : candidateDemands) {
            if (demand.getOrganisation().getId().equals(supply.getOrganisation().getId())) {
                continue;
            }

            MatchResponse result = calculateScore(supply, demand, true);
            if (result.getTotalScore() >= 40.0) {
                results.add(result);
            }
        }

        results.sort(Comparator.comparingDouble(MatchResponse::getTotalScore).reversed());
        return results;
    }

    public List<MatchResponse> discoverMatchesForDemand(Demand demand) {
        List<Unit> compatibleUnits = getCompatibleUnits(demand.getUnit());

        List<Supply> candidateSupplies = supplyRepository.findByStatusAndCategoryIdAndUnitIn(
                Status.ACTIVE, demand.getCategory().getId(), compatibleUnits);

        List<MatchResponse> results = new ArrayList<>();

        for (Supply supply : candidateSupplies) {
            if (supply.getOrganisation().getId().equals(demand.getOrganisation().getId())) {
                continue;
            }

            MatchResponse result = calculateScore(supply, demand, false);
            if (result.getTotalScore() >= 40.0) {
                results.add(result);
            }
        }

        results.sort(Comparator.comparingDouble(MatchResponse::getTotalScore).reversed());
        return results;
    }

    private MatchResponse calculateScore(Supply supply, Demand demand, boolean findingDemandsForSupply) {

        double sQty = normalizeQuantity(supply.getQuantity().doubleValue(), supply.getUnit());
        double dQty = normalizeQuantity(demand.getQuantity().doubleValue(), demand.getUnit());

        double sPrice = normalizePrice(supply.getPricePerUnit().doubleValue(), supply.getUnit());
        double dPrice = normalizePrice(demand.getPricePerUnit().doubleValue(), demand.getUnit());




        double nameSim = calculateSimilarity(supply.getName(), demand.getName());
        double descSim = calculateSimilarity(supply.getDescription(), demand.getDescription());
        double textScore = (25.0 * nameSim) + (15.0 * descSim);


        double priceScore = 0.0;
        if (sPrice <= dPrice) {
            priceScore = 25.0;
        } else {
            priceScore = 25.0 * Math.max(0, 1.0 - ((sPrice - dPrice) / dPrice));
        }


        double distanceKm = calculateHaversine(
                supply.getOrganisation().getLatitude(), supply.getOrganisation().getLongitude(),
                demand.getOrganisation().getLatitude(), demand.getOrganisation().getLongitude()
        );

        double maxAllowedDistance = 500.0;
        double distanceScore = 20.0 * Math.max(0, 1.0 - (distanceKm / maxAllowedDistance));


        double quantityScore = 15.0 * Math.min(1.0, sQty / dQty);


        double totalScore = textScore + priceScore + distanceScore + quantityScore;

        if (findingDemandsForSupply) {

            return MatchResponse.builder()
                    .id(demand.getId())
                    .name(demand.getName())
                    .description(demand.getDescription())
                    .orgName(demand.getOrganisation().getName())
                    .orgContact(demand.getOrganisation().getContactNumber())
                    .pricePerUnit(demand.getPricePerUnit())
                    .quantity(demand.getQuantity())
                    .unit(demand.getUnit().name())
                    .distanceKm(Math.round(distanceKm * 100.0) / 100.0)
                    .latitude(demand.getOrganisation().getLatitude())
                    .longitude(demand.getOrganisation().getLongitude())
                    .textScore(Math.round(textScore * 100.0) / 100.0)
                    .priceScore(Math.round(priceScore * 100.0) / 100.0)
                    .distanceScore(Math.round(distanceScore * 100.0) / 100.0)
                    .quantityScore(Math.round(quantityScore * 100.0) / 100.0)
                    .totalScore(Math.round(totalScore * 100.0) / 100.0)
                    .build();
        } else {

            return MatchResponse.builder()
                    .id(supply.getId())
                    .name(supply.getName())
                    .description(supply.getDescription())
                    .orgName(supply.getOrganisation().getName())
                    .orgContact(supply.getOrganisation().getContactNumber())
                    .pricePerUnit(supply.getPricePerUnit())
                    .quantity(supply.getQuantity())
                    .unit(supply.getUnit().name())
                    .distanceKm(Math.round(distanceKm * 100.0) / 100.0)
                    .latitude(supply.getOrganisation().getLatitude())
                    .longitude(supply.getOrganisation().getLongitude())
                    .textScore(Math.round(textScore * 100.0) / 100.0)
                    .priceScore(Math.round(priceScore * 100.0) / 100.0)
                    .distanceScore(Math.round(distanceScore * 100.0) / 100.0)
                    .quantityScore(Math.round(quantityScore * 100.0) / 100.0)
                    .totalScore(Math.round(totalScore * 100.0) / 100.0)
                    .build();
        }
    }


    private List<Unit> getCompatibleUnits(Unit unit) {
        if (unit == Unit.KG || unit == Unit.GM || unit == Unit.MG) {
            return Arrays.asList(Unit.KG, Unit.GM, Unit.MG);
        } else {
            return Arrays.asList(Unit.LITRE, Unit.ML);
        }
    }


    private double normalizeQuantity(double qty, Unit unit) {
        switch (unit) {
            case KG: return qty * 1000.0;
            case GM: return qty;
            case MG: return qty * 0.001;
            case LITRE: return qty * 1000.0;
            case ML: return qty;
            default: return qty;
        }
    }


    private double normalizePrice(double price, Unit unit) {
        switch (unit) {
            case KG: return price / 1000.0;
            case GM: return price;
            case MG: return price / 0.001;
            case LITRE: return price / 1000.0;
            case ML: return price;
            default: return price;
        }
    }


    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equalsIgnoreCase(s2)) return 1.0;

        String a = s1.toLowerCase();
        String b = s2.toLowerCase();

        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) return 1.0;

        int distance = computeLevenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLength);
    }

    private int computeLevenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++)
            costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }


    private double calculateHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
