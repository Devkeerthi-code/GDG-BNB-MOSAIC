package com.hruthikesh.ime.repository;

import com.hruthikesh.ime.entity.NegotiationTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NegotiationTurnRepository extends JpaRepository<NegotiationTurn, Long> {

    @Query("""
            SELECT t FROM NegotiationTurn t
            JOIN FETCH t.speakingForOrg
            WHERE t.negotiation.id = :negotiationId
            ORDER BY t.roundNumber ASC, t.id ASC
            """)
    List<NegotiationTurn> findByNegotiation_IdOrderByRoundNumberAsc(@Param("negotiationId") Long negotiationId);
}
