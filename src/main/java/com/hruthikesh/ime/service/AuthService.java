package com.hruthikesh.ime.service;

import com.hruthikesh.ime.dto.LoginRequest;
import com.hruthikesh.ime.dto.RegisterRequest;
import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.repository.OrganisationRepository;
import com.hruthikesh.ime.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final OrganisationRepository organisationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthService(OrganisationRepository organisationRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.organisationRepository = organisationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (organisationRepository.existsByContactNumber(request.getContactNumber())) {
            throw new RuntimeException("Error: Contact number is already in use!");
        }

        Organisation org = Organisation.builder()
                .name(request.getName())
                .contactNumber(request.getContactNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        organisationRepository.save(org);
    }

    public String login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getContactNumber(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        return jwtUtil.generateTokenFromContactNumber(request.getContactNumber());
    }
}
