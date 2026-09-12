package com.hruthikesh.ime.controller;

import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.repository.OrganisationRepository;
import com.hruthikesh.ime.service.DemandService;
import com.hruthikesh.ime.service.SupplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final SupplyService supplyService;
    private final DemandService demandService;
    private final OrganisationRepository organisationRepository;

    @Autowired
    public DashboardController(SupplyService supplyService, DemandService demandService, OrganisationRepository organisationRepository) {
        this.supplyService = supplyService;
        this.demandService = demandService;
        this.organisationRepository = organisationRepository;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String contactNumber = auth.getName();

        Organisation org = organisationRepository.findByContactNumber(contactNumber)
                .orElseThrow(() -> new RuntimeException("Organisation not found"));

        model.addAttribute("orgName", org.getName());
        model.addAttribute("supplies", supplyService.getActiveSuppliesByOrg(org.getId()));
        model.addAttribute("demands", demandService.getActiveDemandsByOrg(org.getId()));

        return "dashboard";
    }
}
