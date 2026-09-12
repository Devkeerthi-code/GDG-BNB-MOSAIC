package com.hruthikesh.ime.security;

import com.hruthikesh.ime.entity.Organisation;
import com.hruthikesh.ime.repository.OrganisationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final OrganisationRepository organisationRepository;

    @Autowired
    public CustomUserDetailsService(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String contactNumber) throws UsernameNotFoundException {
        Organisation organisation = organisationRepository.findByContactNumber(contactNumber)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with contact number: " + contactNumber));

        return CustomUserDetails.build(organisation);
    }
}
