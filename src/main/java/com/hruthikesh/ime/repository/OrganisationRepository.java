package com.hruthikesh.ime.repository;

import com.hruthikesh.ime.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
    Optional<Organisation> findByContactNumber(String contactNumber);
    boolean existsByContactNumber(String contactNumber);
    Optional<Organisation> findByName(String name);
}
