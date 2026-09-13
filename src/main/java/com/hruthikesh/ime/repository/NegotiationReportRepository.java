package com.hruthikesh.ime.repository;

import com.hruthikesh.ime.entity.NegotiationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NegotiationReportRepository extends JpaRepository<NegotiationReport, Long> {

    @Query("""
            SELECT r FROM NegotiationReport r
            JOIN FETCH r.forOrg
            JOIN FETCH r.counterpartOrg
            WHERE r.negotiation.id = :negotiationId AND r.forOrg.id = :orgId
            """)
    Optional<NegotiationReport> findByNegotiation_IdAndForOrg_Id(
            @Param("negotiationId") Long negotiationId, @Param("orgId") Long orgId);

    boolean existsByNegotiation_Id(Long negotiationId);
}
