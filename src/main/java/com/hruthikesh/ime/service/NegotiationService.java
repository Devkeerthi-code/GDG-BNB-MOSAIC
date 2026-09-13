package com.hruthikesh.ime.service;

import com.hruthikesh.ime.dto.*;
import com.hruthikesh.ime.entity.*;
import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import com.hruthikesh.ime.entity.enums.Status;
import com.hruthikesh.ime.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NegotiationService {

    private final NegotiationRepository negotiationRepository;
    private final NegotiationTurnRepository turnRepository;
    private final NegotiationReportRepository reportRepository;
    private final SupplyRepository supplyRepository;
    private final DemandRepository demandRepository;
    private final NegotiationAgentClient agentClient;
    private final MatchingAlgorithmService matchingAlgorithmService;
        @Value("${negotiation.max-rounds:3}")
        private int maxRounds = 3;

    @Autowired
    public NegotiationService(
            NegotiationRepository negotiationRepository,
            NegotiationTurnRepository turnRepository,
            NegotiationReportRepository reportRepository,
            SupplyRepository supplyRepository,
            DemandRepository demandRepository,
            NegotiationAgentClient agentClient,
            MatchingAlgorithmService matchingAlgorithmService) {
        this.negotiationRepository = negotiationRepository;
        this.turnRepository = turnRepository;
        this.reportRepository = reportRepository;
        this.supplyRepository = supplyRepository;
        this.demandRepository = demandRepository;
        this.agentClient = agentClient;
        this.matchingAlgorithmService = matchingAlgorithmService;
    }

    @Transactional
    public Negotiation startNegotiation(Long supplyId, Long demandId, Organisation initiator) {
        Supply supply = supplyRepository.findById(supplyId)
                .orElseThrow(() -> new RuntimeException("Supply not found"));
        Demand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new RuntimeException("Demand not found"));

        if (supply.getStatus() != Status.ACTIVE || demand.getStatus() != Status.ACTIVE) {
            throw new RuntimeException("Supply and demand must be active");
        }

        Long initiatorId = initiator.getId();
        if (!supply.getOrganisation().getId().equals(initiatorId)
                && !demand.getOrganisation().getId().equals(initiatorId)) {
            throw new RuntimeException("Unauthorized");
        }

        boolean duplicate = negotiationRepository.existsBySupply_IdAndDemand_IdAndStatusIn(
                supplyId, demandId, List.of(NegotiationStatus.PENDING, NegotiationStatus.IN_PROGRESS));
        if (duplicate) {
            throw new RuntimeException("A negotiation is already in progress for this supply and demand");
        }

        MatchResponse matchScore = matchingAlgorithmService.computeMatchScore(supply, demand);

        Negotiation negotiation = Negotiation.builder()
                .supply(supply)
                .demand(demand)
                .supplierOrg(supply.getOrganisation())
                .demanderOrg(demand.getOrganisation())
                .initiatedByOrg(initiator)
                .status(NegotiationStatus.PENDING)
                .matchScoreAtStart(matchScore.getTotalScore())
                .maxRounds(maxRounds)
                .currentRound(0)
                .build();

        return negotiationRepository.save(negotiation);
    }

    @Transactional
    public void generateReports(Long negotiationId) {
        Negotiation negotiation = negotiationRepository.findByIdWithDetails(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));

        if (reportRepository.existsByNegotiation_Id(negotiationId)) {
            return;
        }

        List<NegotiationTurn> turns = turnRepository.findByNegotiation_IdOrderByRoundNumberAsc(negotiationId);
        List<NegotiationAgentClient.TurnHistoryEntry> history = new ArrayList<>();
        for (NegotiationTurn turn : turns) {
            history.add(new NegotiationAgentClient.TurnHistoryEntry(
                    turn.getAgentRole().name(),
                    turn.getMessage(),
                    turn.getProposedPricePerUnit(),
                    turn.getProposedQuantity()
            ));
        }

        persistReport(negotiation, negotiation.getSupplierOrg(), negotiation.getDemanderOrg(), history);
        persistReport(negotiation, negotiation.getDemanderOrg(), negotiation.getSupplierOrg(), history);
    }

    private void persistReport(
            Negotiation negotiation,
            Organisation forOrg,
            Organisation counterpartOrg,
            List<NegotiationAgentClient.TurnHistoryEntry> history) {

        NegotiationAgentClient.ReportContext context = new NegotiationAgentClient.ReportContext(
                forOrg.getName(),
                counterpartOrg.getName(),
                negotiation.getSupply().getName(),
                negotiation.getStatus(),
                negotiation.getAgreedPricePerUnit(),
                negotiation.getAgreedQuantity(),
                negotiation.getSupply().getUnit().name(),
                negotiation.getFailureReason(),
                history
        );

        NegotiationAgentClient.ReportResult result = agentClient.generateReport(context);

        NegotiationReport report = NegotiationReport.builder()
                .negotiation(negotiation)
                .forOrg(forOrg)
                .counterpartOrg(counterpartOrg)
                .outcome(negotiation.getStatus())
                .summary(result.summary())
                .recommendation(result.recommendation())
                .dealTerms(result.dealTerms())
                .generatedAt(LocalDateTime.now())
                .build();

        reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public Negotiation getNegotiation(Long id, Long requestingOrgId) {
        Negotiation negotiation = negotiationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));
        assertParty(negotiation, requestingOrgId);
        return negotiation;
    }

    @Transactional(readOnly = true)
    public NegotiationStatusResponse getStatus(Long negotiationId, Long requestingOrgId) {
        Negotiation negotiation = getNegotiation(negotiationId, requestingOrgId);
        List<NegotiationTurnDto> turns = mapTurns(
                turnRepository.findByNegotiation_IdOrderByRoundNumberAsc(negotiation.getId()));

        boolean terminal = negotiation.getStatus() == NegotiationStatus.DEAL_REACHED
                || negotiation.getStatus() == NegotiationStatus.NO_DEAL
                || negotiation.getStatus() == NegotiationStatus.FAILED;

        return NegotiationStatusResponse.builder()
                .status(negotiation.getStatus())
                .currentRound(negotiation.getCurrentRound())
                .maxRounds(negotiation.getMaxRounds())
                .turns(turns)
                .reportAvailable(terminal && reportRepository.existsByNegotiation_Id(negotiationId))
                .failureReason(negotiation.getFailureReason())
                .build();
    }

    @Transactional(readOnly = true)
    public NegotiationReportDto getReportForOrg(Long negotiationId, Long orgId) {
        getNegotiation(negotiationId, orgId);
        NegotiationReport report = reportRepository.findByNegotiation_IdAndForOrg_Id(negotiationId, orgId)
                .orElseThrow(() -> new RuntimeException("Report not available yet"));

        return NegotiationReportDto.builder()
                .negotiationId(negotiationId)
                .forOrgName(report.getForOrg().getName())
                .counterpartOrgName(report.getCounterpartOrg().getName())
                .outcome(report.getOutcome())
                .summary(report.getSummary())
                .recommendation(report.getRecommendation())
                .dealTerms(report.getDealTerms())
                .generatedAt(report.getGeneratedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<Negotiation> listMyNegotiations(Long orgId) {
        return negotiationRepository.findBySupplierOrg_IdOrDemanderOrg_Id(orgId);
    }

    private void assertParty(Negotiation negotiation, Long orgId) {
        if (!negotiation.getSupplierOrg().getId().equals(orgId)
                && !negotiation.getDemanderOrg().getId().equals(orgId)) {
            throw new RuntimeException("Unauthorized");
        }
    }

    private List<NegotiationTurnDto> mapTurns(List<NegotiationTurn> turns) {
        return turns.stream().map(t -> NegotiationTurnDto.builder()
                .id(t.getId())
                .roundNumber(t.getRoundNumber())
                .agentRole(t.getAgentRole())
                .speakingForOrgName(t.getSpeakingForOrg().getName())
                .message(t.getMessage())
                .proposedPricePerUnit(t.getProposedPricePerUnit())
                .proposedQuantity(t.getProposedQuantity())
                .decision(t.getDecision())
                .createdAt(t.getCreatedAt())
                .build()).toList();
    }
}
