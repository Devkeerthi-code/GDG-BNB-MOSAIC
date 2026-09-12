package com.hruthikesh.ime.service;

import com.hruthikesh.ime.dto.ItemRequest;
import com.hruthikesh.ime.entity.Category;
import com.hruthikesh.ime.entity.Demand;
import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.entity.enums.Status;
import com.hruthikesh.ime.repository.CategoryRepository;
import com.hruthikesh.ime.repository.DemandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DemandService {

    private final DemandRepository demandRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public DemandService(DemandRepository demandRepository, CategoryRepository categoryRepository) {
        this.demandRepository = demandRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void createDemand(ItemRequest dto, Organisation org) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Demand demand = Demand.builder()
                .name(dto.getName())
                .category(category)
                .organisation(org)
                .description(dto.getDescription())
                .pricePerUnit(dto.getPricePerUnit())
                .quantity(dto.getQuantity())
                .unit(dto.getUnit())
                .maxDistance(dto.getMaxDistance())
                .status(Status.ACTIVE)
                .build();

        demandRepository.save(demand);
    }

    public List<Demand> getActiveDemandsByOrg(Long orgId) {
        return demandRepository.findByOrganisationIdAndStatus(orgId, Status.ACTIVE);
    }

    @Transactional
    public void deleteDemand(Long id, Long orgId) {
        Demand demand = demandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demand not found"));

        if (!demand.getOrganisation().getId().equals(orgId)) {
            throw new RuntimeException("Unauthorized");
        }

        demandRepository.delete(demand);
    }

    public Demand getDemandById(Long id) {
        return demandRepository.findById(id).orElseThrow(() -> new RuntimeException("Demand not found"));
    }
}
