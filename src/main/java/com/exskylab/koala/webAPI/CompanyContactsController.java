package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/company-contacts")
public class CompanyContactsController {

    private final CompanyContactService companyContactService;


    public CompanyContactsController(CompanyContactService companyContactService) {
        this.companyContactService = companyContactService;
    }

}
