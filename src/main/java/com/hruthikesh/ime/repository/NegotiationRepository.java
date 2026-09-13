package com.hruthikesh.ime.repository;

import com.hruthikesh.ime.entity.Negotiation;
import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NegotiationRepository extends JpaRepository<Negotiation, Long> {

    @Query("""
            SELECT n FROM Negotiation n
            JOIN FETCH n.supply s JOIN FETCH s.category JOIN FETCH s.organisation
            JOIN FETCH n.demand d JOIN FETCH d.category JOIN FETCH d.organisation
            JOIN FETCH n.supplierOrg JOIN FETCH n.demanderOrg JOIN FETCH n.initiatedByOrg
            WHERE n.id = :id
            """)
    Optional<Negotiation> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT n FROM Negotiation n
            JOIN FETCH n.supply s
            JOIN FETCH n.demand d
            JOIN FETCH n.supplierOrg
            JOIN FETCH n.demanderOrg
            WHERE n.supplierOrg.id = :orgId OR n.demanderOrg.id = :orgId
            ORDER BY n.createdAt DESC
            """)
    List<Negotiation> findBySupplierOrg_IdOrDemanderOrg_Id(@Param("orgId") Long orgId);

    boolean existsBySupply_IdAndDemand_IdAndStatusIn(
            Long supplyId, Long demandId, List<NegotiationStatus> statuses);
}
