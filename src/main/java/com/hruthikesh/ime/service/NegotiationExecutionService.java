package com.hruthikesh.ime.service;

import com.hruthikesh.ime.dto.MatchResponse;
import com.hruthikesh.ime.entity.*;
import com.hruthikesh.ime.entity.enums.AgentRole;
import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import com.hruthikesh.ime.entity.enums.TurnDecision;
import com.hruthikesh.ime.repository.NegotiationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class NegotiationExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(NegotiationExecutionService.class);

    private final NegotiationRepository negotiationRepository;
    private final NegotiationAgentClient agentClient;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final NegotiationService negotiationService;
    private final NegotiationPersistenceService persistenceService;

    @Autowired
    public NegotiationExecutionService(
            NegotiationRepository negotiationRepository,
            NegotiationAgentClient agentClient,
            MatchingAlgorithmService matchingAlgorithmService,
            NegotiationService negotiationService,
            NegotiationPersistenceService persistenceService) {
        this.negotiationRepository = negotiationRepository;
        this.agentClient = agentClient;
        this.matchingAlgorithmService = matchingAlgorithmService;
        this.negotiationService = negotiationService;
        this.persistenceService = persistenceService;
    }

    @Async("negotiationExecutor")
    public void runNegotiationAsync(Long negotiationId) {
        try {
            executeNegotiation(negotiationId);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            logger.error("Negotiation {} failed: {}", negotiationId, reason, e);
            persistenceService.markFailed(negotiationId, reason);
        }
    }

    public void executeNegotiation(Long negotiationId) {
        Negotiation negotiation = negotiationRepository.findByIdWithDetails(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));

        persistenceService.updateStatus(negotiationId, NegotiationStatus.IN_PROGRESS, null);

        Supply supply = negotiation.getSupply();
        Demand demand = negotiation.getDemand();
        MatchResponse match = matchingAlgorithmService.computeMatchScore(supply, demand);
        double distanceKm = match.getDistanceKm();

        List<NegotiationTurn> turns = new ArrayList<>();
        BigDecimal lastSupplierPrice = supply.getPricePerUnit();
        BigDecimal lastSupplierQty = supply.getQuantity();
        BigDecimal lastDemanderPrice = demand.getPricePerUnit();
        BigDecimal lastDemanderQty = demand.getQuantity();
        String lastSupplierMessage = null;
        String lastDemanderMessage = null;

        boolean finished = false;
        int maxRounds = negotiation.getMaxRounds();

        for (int round = 1; round <= maxRounds && !finished; round++) {
            persistenceService.updateCurrentRound(negotiationId, round);

            NegotiationAgentClient.AgentTurnResult supplierResult = agentClient.generateTurn(
                    buildTurnContext(negotiation, AgentRole.SUPPLIER_AGENT, round, distanceKm, turns,
                            lastDemanderMessage, lastDemanderPrice, lastDemanderQty));

            NegotiationTurn supplierTurn = persistenceService.saveTurn(
                    negotiationId, round, AgentRole.SUPPLIER_AGENT, supplierResult);
            turns.add(supplierTurn);

            lastSupplierPrice = coalesce(supplierResult.proposedPricePerUnit(), lastSupplierPrice);
            lastSupplierQty = coalesce(supplierResult.proposedQuantity(), lastSupplierQty);
            lastSupplierMessage = supplierResult.message();

            if (supplierResult.decision() == TurnDecision.ACCEPT) {
                persistenceService.finalizeDeal(negotiationId, lastSupplierPrice, lastSupplierQty, supplierResult.message());
                finished = true;
                break;
            }
            if (supplierResult.decision() == TurnDecision.REJECT) {
                persistenceService.finalizeNoDeal(negotiationId, "Supplier agent rejected the deal");
                finished = true;
                break;
            }

            NegotiationAgentClient.AgentTurnResult demanderResult = agentClient.generateTurn(
                    buildTurnContext(negotiation, AgentRole.DEMANDER_AGENT, round, distanceKm, turns,
                            lastSupplierMessage, lastSupplierPrice, lastSupplierQty));

            NegotiationTurn demanderTurn = persistenceService.saveTurn(
                    negotiationId, round, AgentRole.DEMANDER_AGENT, demanderResult);
            turns.add(demanderTurn);

            lastDemanderPrice = coalesce(demanderResult.proposedPricePerUnit(), lastDemanderPrice);
            lastDemanderQty = coalesce(demanderResult.proposedQuantity(), lastDemanderQty);
            lastDemanderMessage = demanderResult.message();

            if (demanderResult.decision() == TurnDecision.ACCEPT) {
                persistenceService.finalizeDeal(negotiationId, lastDemanderPrice, lastDemanderQty, demanderResult.message());
                finished = true;
            } else if (demanderResult.decision() == TurnDecision.REJECT) {
                persistenceService.finalizeNoDeal(negotiationId, "Demander agent rejected the deal");
                finished = true;
            }
        }

        if (!finished) {
            if (withinTolerance(lastSupplierPrice, lastDemanderPrice)
                    && withinQuantity(lastSupplierQty, lastDemanderQty, supply.getQuantity(), demand.getQuantity())) {
                persistenceService.finalizeDeal(negotiationId,
                        averagePrice(lastSupplierPrice, lastDemanderPrice),
                        minQuantity(lastSupplierQty, lastDemanderQty, supply.getQuantity(), demand.getQuantity()),
                        "Agreement reached within round limit");
            } else {
                persistenceService.finalizeNoDeal(negotiationId, "No agreement within round limit");
            }
        }

        negotiationService.generateReports(negotiationId);
    }

    private NegotiationAgentClient.NegotiationContext buildTurnContext(
            Negotiation negotiation,
            AgentRole role,
            int round,
            double distanceKm,
            List<NegotiationTurn> turns,
            String counterpartMessage,
            BigDecimal counterpartPrice,
            BigDecimal counterpartQty) {

        Supply supply = negotiation.getSupply();
        Demand demand = negotiation.getDemand();
        boolean supplierSpeaking = role == AgentRole.SUPPLIER_AGENT;

        List<NegotiationAgentClient.TurnHistoryEntry> history = new ArrayList<>();
        for (NegotiationTurn turn : turns) {
            history.add(new NegotiationAgentClient.TurnHistoryEntry(
                    turn.getAgentRole().name(),
                    turn.getMessage(),
                    turn.getProposedPricePerUnit(),
                    turn.getProposedQuantity()
            ));
        }

        return new NegotiationAgentClient.NegotiationContext(
                role,
                supplierSpeaking ? negotiation.getSupplierOrg().getName() : negotiation.getDemanderOrg().getName(),
                supplierSpeaking ? negotiation.getDemanderOrg().getName() : negotiation.getSupplierOrg().getName(),
                supply.getName(),
                supply.getDescription(),
                supply.getCategory().getName(),
                supplierSpeaking ? supply.getPricePerUnit() : demand.getPricePerUnit(),
                supplierSpeaking ? supply.getQuantity() : demand.getQuantity(),
                supply.getUnit().name(),
                counterpartPrice,
                counterpartQty,
                counterpartMessage,
                round,
                negotiation.getMaxRounds(),
                distanceKm,
                history
        );
    }

    private BigDecimal coalesce(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private boolean withinTolerance(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        BigDecimal pct = a.subtract(b).abs().divide(b, 4, RoundingMode.HALF_UP);
        return pct.compareTo(new BigDecimal("0.05")) <= 0;
    }

    private boolean withinQuantity(BigDecimal a, BigDecimal b, BigDecimal supplyQty, BigDecimal demandQty) {
        if (a == null || b == null) {
            return false;
        }
        BigDecimal agreed = a.min(b);
        return agreed.compareTo(supplyQty.min(demandQty)) <= 0 && agreed.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal averagePrice(BigDecimal a, BigDecimal b) {
        return a.add(b).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal minQuantity(BigDecimal a, BigDecimal b, BigDecimal supplyQty, BigDecimal demandQty) {
        return a.min(b).min(supplyQty).min(demandQty);
    }
}
