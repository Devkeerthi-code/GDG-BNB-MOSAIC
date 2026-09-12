package com.hruthikesh.ime.repository;

import com.hruthikesh.ime.entity.Demand;
import com.hruthikesh.ime.entity.enums.Status;
import com.hruthikesh.ime.entity.enums.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandRepository extends JpaRepository<Demand, Long> {
    List<Demand> findByOrganisationIdAndStatus(Long organisationId, Status status);


    List<Demand> findByStatusAndCategoryIdAndUnitIn(Status status, Long categoryId, List<Unit> units);
}
