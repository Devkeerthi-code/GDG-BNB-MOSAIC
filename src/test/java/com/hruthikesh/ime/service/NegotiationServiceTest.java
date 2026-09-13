package com.hruthikesh.ime.service;

import com.hruthikesh.ime.dto.MatchResponse;
import com.hruthikesh.ime.entity.*;
import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import com.hruthikesh.ime.entity.enums.Status;
import com.hruthikesh.ime.entity.enums.Unit;
import com.hruthikesh.ime.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NegotiationServiceTest {

    @Mock private NegotiationRepository negotiationRepository;
    @Mock private NegotiationTurnRepository turnRepository;
    @Mock private NegotiationReportRepository reportRepository;
    @Mock private SupplyRepository supplyRepository;
    @Mock private DemandRepository demandRepository;
    @Mock private NegotiationAgentClient agentClient;
    @Mock private MatchingAlgorithmService matchingAlgorithmService;

    @InjectMocks
    private NegotiationService negotiationService;

    private Organisation supplierOrg;
    private Organisation demanderOrg;
    private Supply supply;
    private Demand demand;

    @BeforeEach
    void setUp() {
        supplierOrg = Organisation.builder().id(1L).name("Supplier Co").contactNumber("111").build();
        demanderOrg = Organisation.builder().id(2L).name("Demander Co").contactNumber("222").build();

        Category category = Category.builder().id(1L).name("Steel").build();

        supply = Supply.builder()
                .id(10L).name("Steel Rods").category(category).organisation(supplierOrg)
                .pricePerUnit(new BigDecimal("100")).quantity(new BigDecimal("500"))
                .unit(Unit.KG).status(Status.ACTIVE).build();

        demand = Demand.builder()
                .id(20L).name("Steel Need").category(category).organisation(demanderOrg)
                .pricePerUnit(new BigDecimal("110")).quantity(new BigDecimal("400"))
                .unit(Unit.KG).status(Status.ACTIVE).build();
    }

    @Test
    void startNegotiation_createsPendingNegotiation() {
        when(supplyRepository.findById(10L)).thenReturn(Optional.of(supply));
        when(demandRepository.findById(20L)).thenReturn(Optional.of(demand));
        when(negotiationRepository.existsBySupply_IdAndDemand_IdAndStatusIn(anyLong(), anyLong(), anyList()))
                .thenReturn(false);
        when(matchingAlgorithmService.computeMatchScore(supply, demand))
                .thenReturn(MatchResponse.builder().totalScore(85.0).build());
        when(negotiationRepository.save(any(Negotiation.class))).thenAnswer(inv -> {
            Negotiation n = inv.getArgument(0);
            n.setId(99L);
            return n;
        });

        Negotiation result = negotiationService.startNegotiation(10L, 20L, supplierOrg);

        assertEquals(NegotiationStatus.PENDING, result.getStatus());
        assertEquals(99L, result.getId());
        assertEquals(85.0, result.getMatchScoreAtStart());
    }

    @Test
    void startNegotiation_rejectsNonParty() {
        when(supplyRepository.findById(10L)).thenReturn(Optional.of(supply));
        when(demandRepository.findById(20L)).thenReturn(Optional.of(demand));

        Organisation outsider = Organisation.builder().id(99L).build();

        assertThrows(RuntimeException.class,
                () -> negotiationService.startNegotiation(10L, 20L, outsider));
    }

    @Test
    void getReportForOrg_rejectsNonParty() {
        Negotiation negotiation = Negotiation.builder()
                .id(1L).supplierOrg(supplierOrg).demanderOrg(demanderOrg).build();

        when(negotiationRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(negotiation));

        assertThrows(RuntimeException.class,
                () -> negotiationService.getReportForOrg(1L, 99L));
    }
}
