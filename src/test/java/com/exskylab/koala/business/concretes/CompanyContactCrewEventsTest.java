package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.events.CompanyContactAddedEvent;
import com.exskylab.koala.core.events.CompanyContactRemovedEvent;
import com.exskylab.koala.dataAccess.CompanyContactDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyContactCrewEventsTest {

    private final CompanyContactDao companyContactDao = mock(CompanyContactDao.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final CompanyContactManager manager = new CompanyContactManager(companyContactDao, eventPublisher);

    private final Company company = company();
    private final User officer = user();

    private static Company company() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        return company;
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void addingAContactAnnouncesItAfterTheRowIsSaved() {
        when(companyContactDao.save(any())).thenAnswer(call -> {
            CompanyContact saved = call.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        manager.addCompanyContact(company, officer, CompanyContactRole.STAFF);

        InOrder order = inOrder(companyContactDao, eventPublisher);
        order.verify(companyContactDao).save(any(CompanyContact.class));
        order.verify(eventPublisher).publishEvent(new CompanyContactAddedEvent(company.getId(), officer.getId()));
    }

    @Test
    void removingAContactAnnouncesTheCompanyAndTheUserItLeft() {
        CompanyContact contact = new CompanyContact();
        contact.setId(UUID.randomUUID());
        contact.setCompany(company);
        contact.setUser(officer);
        contact.setRole(CompanyContactRole.ADMIN);

        manager.removeContact(contact);

        verify(companyContactDao).delete(contact);
        verify(eventPublisher).publishEvent(new CompanyContactRemovedEvent(company.getId(), officer.getId()));
    }
}
