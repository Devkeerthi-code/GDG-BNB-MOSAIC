package com.hruthikesh.ime.repository;

import com.hruthikesh.ime.entity.Supply;
import com.hruthikesh.ime.entity.enums.Status;
import com.hruthikesh.ime.entity.enums.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplyRepository extends JpaRepository<Supply, Long> {
    List<Supply> findByOrganisationIdAndStatus(Long organisationId, Status status);


    List<Supply> findByStatusAndCategoryIdAndUnitIn(Status status, Long categoryId, List<Unit> units);
}
