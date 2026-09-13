package com.hruthikesh.ime.controller;

import com.hruthikesh.ime.dto.NegotiationReportDto;
import com.hruthikesh.ime.dto.NegotiationStartRequest;
import com.hruthikesh.ime.dto.NegotiationStatusResponse;
import com.hruthikesh.ime.entity.Negotiation;
import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.repository.OrganisationRepository;
import com.hruthikesh.ime.service.NegotiationExecutionService;
import com.hruthikesh.ime.service.NegotiationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/negotiations")
public class NegotiationController {

    private final NegotiationService negotiationService;
    private final NegotiationExecutionService negotiationExecutionService;
    private final OrganisationRepository organisationRepository;

    @Autowired
    public NegotiationController(
            NegotiationService negotiationService,
            NegotiationExecutionService negotiationExecutionService,
            OrganisationRepository organisationRepository) {
        this.negotiationService = negotiationService;
        this.negotiationExecutionService = negotiationExecutionService;
        this.organisationRepository = organisationRepository;
    }

    @PostMapping("/start")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> start(@RequestBody NegotiationStartRequest request) {
        Organisation org = getCurrentOrg();
        Negotiation negotiation = negotiationService.startNegotiation(
                request.getSupplyId(), request.getDemandId(), org);
        negotiationExecutionService.runNegotiationAsync(negotiation.getId());
        return ResponseEntity.ok(Map.of("negotiationId", negotiation.getId()));
    }

    @GetMapping
    public String listNegotiations(Model model) {
        Organisation org = getCurrentOrg();
        List<Negotiation> negotiations = negotiationService.listMyNegotiations(org.getId());
        model.addAttribute("negotiations", negotiations);
        model.addAttribute("orgId", org.getId());
        return "negotiations";
    }

    @GetMapping("/{id}")
    public String viewNegotiation(@PathVariable Long id, Model model) {
        Organisation org = getCurrentOrg();
        Negotiation negotiation = negotiationService.getNegotiation(id, org.getId());

        model.addAttribute("negotiation", negotiation);
        model.addAttribute("supply", negotiation.getSupply());
        model.addAttribute("demand", negotiation.getDemand());
        model.addAttribute("supplierOrg", negotiation.getSupplierOrg());
        model.addAttribute("demanderOrg", negotiation.getDemanderOrg());
        model.addAttribute("currentOrgId", org.getId());
        return "negotiation";
    }

    @GetMapping("/{id}/status")
    @ResponseBody
    public NegotiationStatusResponse status(@PathVariable Long id) {
        Organisation org = getCurrentOrg();
        return negotiationService.getStatus(id, org.getId());
    }

    @GetMapping("/{id}/report")
    public String viewReport(@PathVariable Long id, Model model) {
        Organisation org = getCurrentOrg();
        NegotiationReportDto report = negotiationService.getReportForOrg(id, org.getId());
        model.addAttribute("report", report);
        return "negotiation-report";
    }

    @GetMapping("/{id}/report/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        Organisation org = getCurrentOrg();
        NegotiationReportDto report = negotiationService.getReportForOrg(id, org.getId());

        String content = "Deal Report\n\n"
                + "Prepared for: " + report.getForOrgName() + "\n"
                + "Counterpart: " + report.getCounterpartOrgName() + "\n"
                + "Outcome: " + report.getOutcome() + "\n\n"
                + "Summary\n" + report.getSummary() + "\n\n"
                + "Recommendation\n" + report.getRecommendation() + "\n"
                + (report.getDealTerms() == null ? "" : "\nAgreed Terms\n" + report.getDealTerms() + "\n")
                + "\nGenerated: " + report.getGeneratedAt() + "\n";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=negotiation-" + id + "-report.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    private Organisation getCurrentOrg() {
        String contactNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        return organisationRepository.findByContactNumber(contactNumber)
                .orElseThrow(() -> new RuntimeException("Organisation not found"));
    }
}
