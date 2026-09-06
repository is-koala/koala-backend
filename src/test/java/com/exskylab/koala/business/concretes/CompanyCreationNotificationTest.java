package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.business.abstracts.CompanyContactInvitationService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.CompanyFollowService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.dataAccess.CompanyDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyType;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyCreationNotificationTest {

    private NotificationService notificationService;
    private CompanyManager manager;
    private User creator;

    @BeforeEach
    void setUp() {
        CompanyDao companyDao = mock(CompanyDao.class);
        notificationService = mock(NotificationService.class);
        SecurityService securityService = mock(SecurityService.class);
        AddressService addressService = mock(AddressService.class);

        creator = new User();
        creator.setId(UUID.randomUUID());
        creator.setFirstName("Ada");
        creator.setLastName("Yılmaz");
        creator.setEmail("ada@koala.test");
        creator.setIdentityVerified(true);
        when(securityService.getAuthenticatedUser()).thenReturn(creator);

        Address address = new Address();
        address.setId(UUID.randomUUID());
        when(addressService.createAddress(any())).thenReturn(address);

        when(companyDao.save(any())).thenAnswer(call -> {
            Company saved = call.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.getContacts().forEach(contact -> contact.setId(UUID.randomUUID()));
            return saved;
        });
        when(companyDao.findById(any())).thenAnswer(call -> Optional.of(new Company()));

        manager = new CompanyManager(companyDao, mock(UserService.class), notificationService,
                mock(ImageService.class), mock(CompanyContactInvitationService.class), securityService,
                addressService, mock(EntityManager.class), mock(CompanyMapper.class),
                mock(CompanyContactService.class), mock(GalleryService.class),
                mock(CompanyFollowService.class), new PageableSanitizer());
    }

    private List<SendEmailDto> mailsSentWhileCreating(String companyEmail) {
        CreateCompanyRequestDto request = new CreateCompanyRequestDto();
        request.setName("Koala AŞ");
        request.setEmail(companyEmail);
        request.setPhoneNumber("+905550000000");
        request.setTaxNumber("1234567890");
        request.setType(CompanyType.LIMITED.name());
        request.setAddress(new AddressWriteDto());

        manager.addCompany(request, null);

        ArgumentCaptor<SendEmailDto> captor = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notificationService, times(2))
                .sendEmail(captor.capture(), any(DispatchPriority.class), anyBoolean());
        return captor.getAllValues();
    }

    @Test
    void theMailToTheFounderIsAttributedToThatUser() {
        SendEmailDto toContact = mailsSentWhileCreating("info@koala.test").get(1);

        assertEquals(creator.getEmail(), toContact.getDestinationEmail());
        assertSame(creator, toContact.getRecipient(),
                "kurucuya giden posta bilinen bir kullanıcıya gidiyor, tercihine tabi olmalı");
    }

    @Test
    void theMailToTheCompanyAddressNamesNoRecipientBecauseThatAddressNeedNotBeAKoalaUser() {
        SendEmailDto toCompany = mailsSentWhileCreating("info@koala.test").get(0);

        assertEquals("info@koala.test", toCompany.getDestinationEmail());
        assertNull(toCompany.getRecipient());
    }

    @Test
    void theCompanyAddressStaysExternalEvenWhenItMatchesTheFoundersOwnAddress() {
        SendEmailDto toCompany = mailsSentWhileCreating(creator.getEmail()).get(0);

        assertNull(toCompany.getRecipient());
    }
}
