package com.hruthikesh.ime.controller;

import com.hruthikesh.ime.dto.MatchResponse;
import com.hruthikesh.ime.entity.Supply;
import com.hruthikesh.ime.service.MatchingAlgorithmService;
import com.hruthikesh.ime.service.SupplyService;
import com.hruthikesh.ime.entity.Demand;
import com.hruthikesh.ime.service.DemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/discover")
public class DiscoveryController {

    private final SupplyService supplyService;
    private final DemandService demandService;
    private final MatchingAlgorithmService matchingAlgorithmService;

    @Autowired
    public DiscoveryController(SupplyService supplyService, DemandService demandService, MatchingAlgorithmService matchingAlgorithmService) {
        this.supplyService = supplyService;
        this.demandService = demandService;
        this.matchingAlgorithmService = matchingAlgorithmService;
    }

    @GetMapping("/supply/{supplyId}")
    public String discoverDemandsForSupply(@PathVariable Long supplyId, Model model) {
        Supply supply = supplyService.getSupplyById(supplyId);
        List<MatchResponse> matches = matchingAlgorithmService.discoverMatchesForSupply(supply);

        model.addAttribute("sourceItem", supply);
        model.addAttribute("sourceType", "Supply");
        model.addAttribute("matches", matches);

        return "discover";
    }

    @GetMapping("/demand/{demandId}")
    public String discoverSuppliesForDemand(@PathVariable Long demandId, Model model) {
        Demand demand = demandService.getDemandById(demandId);
        List<MatchResponse> matches = matchingAlgorithmService.discoverMatchesForDemand(demand);

        model.addAttribute("sourceItem", demand);
        model.addAttribute("sourceType", "Demand");
        model.addAttribute("matches", matches);

        return "discover";
    }
}
