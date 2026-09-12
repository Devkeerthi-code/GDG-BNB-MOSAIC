package com.hruthikesh.ime.controller;

import com.hruthikesh.ime.dto.ItemRequest;
import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.entity.enums.Unit;
import com.hruthikesh.ime.repository.CategoryRepository;
import com.hruthikesh.ime.repository.OrganisationRepository;
import com.hruthikesh.ime.service.DemandService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/demands")
public class DemandController {

    private final DemandService demandService;
    private final CategoryRepository categoryRepository;
    private final OrganisationRepository organisationRepository;

    @Autowired
    public DemandController(DemandService demandService, CategoryRepository categoryRepository, OrganisationRepository organisationRepository) {
        this.demandService = demandService;
        this.categoryRepository = categoryRepository;
        this.organisationRepository = organisationRepository;
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("itemRequest", new ItemRequest());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("units", Unit.values());
        return "create-demand";
    }

    @PostMapping("/create")
    public String createDemand(@Valid @ModelAttribute("itemRequest") ItemRequest itemRequest, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("units", Unit.values());
            return "create-demand";
        }

        String contactNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        Organisation org = organisationRepository.findByContactNumber(contactNumber).orElseThrow();

        demandService.createDemand(itemRequest, org);
        return "redirect:/dashboard?success=Demand created";
    }

    @PostMapping("/{id}/delete")
    public String deleteDemand(@PathVariable Long id) {
        String contactNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        Organisation org = organisationRepository.findByContactNumber(contactNumber).orElseThrow();

        demandService.deleteDemand(id, org.getId());
        return "redirect:/dashboard?success=Demand deleted";
    }
}
