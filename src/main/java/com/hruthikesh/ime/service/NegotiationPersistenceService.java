package com.hruthikesh.ime.service;

import com.hruthikesh.ime.entity.Negotiation;
import com.hruthikesh.ime.entity.NegotiationTurn;
import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.entity.enums.AgentRole;
import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import com.hruthikesh.ime.repository.NegotiationRepository;
import com.hruthikesh.ime.repository.NegotiationTurnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class NegotiationPersistenceService {

    private final NegotiationRepository negotiationRepository;
    private final NegotiationTurnRepository turnRepository;

    @Autowired
    public NegotiationPersistenceService(
            NegotiationRepository negotiationRepository,
            NegotiationTurnRepository turnRepository) {
        this.negotiationRepository = negotiationRepository;
        this.turnRepository = turnRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long negotiationId, NegotiationStatus status, String failureReason) {
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.setStatus(status);
        if (failureReason != null) {
            n.setFailureReason(failureReason);
        }
        negotiationRepository.save(n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCurrentRound(Long negotiationId, int round) {
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.setCurrentRound(round);
        negotiationRepository.save(n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NegotiationTurn saveTurn(
            Long negotiationId,
            int round,
            AgentRole role,
            NegotiationAgentClient.AgentTurnResult result) {

        Negotiation negotiation = negotiationRepository.findById(negotiationId).orElseThrow();
        Organisation org = role == AgentRole.SUPPLIER_AGENT
                ? negotiation.getSupplierOrg() : negotiation.getDemanderOrg();

        NegotiationTurn turn = NegotiationTurn.builder()
                .negotiation(negotiation)
                .roundNumber(round)
                .agentRole(role)
                .speakingForOrg(org)
                .message(result.message())
                .proposedPricePerUnit(result.proposedPricePerUnit())
                .proposedQuantity(result.proposedQuantity())
                .decision(result.decision())
                .createdAt(LocalDateTime.now())
                .build();

        return turnRepository.save(turn);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeDeal(Long negotiationId, BigDecimal price, BigDecimal quantity, String terms) {
        Negotiation n = negotiationRepository.findByIdWithDetails(negotiationId).orElseThrow();
        n.setStatus(NegotiationStatus.DEAL_REACHED);
        n.setAgreedPricePerUnit(price);
        n.setAgreedQuantity(quantity);
        n.setAgreedUnit(n.getSupply().getUnit());
        n.setAgreedTerms(terms);
        n.setFailureReason(null);
        n.setCompletedAt(LocalDateTime.now());
        negotiationRepository.save(n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeNoDeal(Long negotiationId, String reason) {
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.setStatus(NegotiationStatus.NO_DEAL);
        n.setFailureReason(reason);
        n.setCompletedAt(LocalDateTime.now());
        negotiationRepository.save(n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long negotiationId, String reason) {
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.setStatus(NegotiationStatus.FAILED);
        n.setFailureReason(reason);
        n.setCompletedAt(LocalDateTime.now());
        negotiationRepository.save(n);
    }
}
