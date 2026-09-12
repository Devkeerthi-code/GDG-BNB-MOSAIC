package com.hruthikesh.ime.service;

import com.hruthikesh.ime.dto.ItemRequest;
import com.hruthikesh.ime.entity.Category;
import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.entity.Supply;
import com.hruthikesh.ime.entity.enums.Status;
import com.hruthikesh.ime.repository.CategoryRepository;
import com.hruthikesh.ime.repository.SupplyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SupplyService {

    private final SupplyRepository supplyRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public SupplyService(SupplyRepository supplyRepository, CategoryRepository categoryRepository) {
        this.supplyRepository = supplyRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void createSupply(ItemRequest dto, Organisation org) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Supply supply = Supply.builder()
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

        supplyRepository.save(supply);
    }

    public List<Supply> getActiveSuppliesByOrg(Long orgId) {
        return supplyRepository.findByOrganisationIdAndStatus(orgId, Status.ACTIVE);
    }

    @Transactional
    public void deleteSupply(Long id, Long orgId) {
        Supply supply = supplyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supply not found"));

        if (!supply.getOrganisation().getId().equals(orgId)) {
            throw new RuntimeException("Unauthorized");
        }

        supplyRepository.delete(supply);
    }

    public Supply getSupplyById(Long id) {
        return supplyRepository.findById(id).orElseThrow(() -> new RuntimeException("Supply not found"));
    }
}
