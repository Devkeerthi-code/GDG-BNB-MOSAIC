package com.hruthikesh.ime.controller;

import com.hruthikesh.ime.dto.ItemRequest;
import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.entity.enums.Unit;
import com.hruthikesh.ime.repository.CategoryRepository;
import com.hruthikesh.ime.repository.OrganisationRepository;
import com.hruthikesh.ime.service.SupplyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/supplies")
public class SupplyController {

    private final SupplyService supplyService;
    private final CategoryRepository categoryRepository;
    private final OrganisationRepository organisationRepository;

    @Autowired
    public SupplyController(SupplyService supplyService, CategoryRepository categoryRepository, OrganisationRepository organisationRepository) {
        this.supplyService = supplyService;
        this.categoryRepository = categoryRepository;
        this.organisationRepository = organisationRepository;
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("itemRequest", new ItemRequest());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("units", Unit.values());
        return "create-supply";
    }

    @PostMapping("/create")
    public String createSupply(@Valid @ModelAttribute("itemRequest") ItemRequest itemRequest, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("units", Unit.values());
            return "create-supply";
        }

        String contactNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        Organisation org = organisationRepository.findByContactNumber(contactNumber).orElseThrow();

        supplyService.createSupply(itemRequest, org);
        return "redirect:/dashboard?success=Supply created";
    }

    @PostMapping("/{id}/delete")
    public String deleteSupply(@PathVariable Long id) {
        String contactNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        Organisation org = organisationRepository.findByContactNumber(contactNumber).orElseThrow();

        supplyService.deleteSupply(id, org.getId());
        return "redirect:/dashboard?success=Supply deleted";
    }
}
