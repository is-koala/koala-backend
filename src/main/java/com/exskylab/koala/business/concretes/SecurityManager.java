package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.SystemUser;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SecurityManager implements SecurityService {

    private final UserDao userDao;

    public SecurityManager(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserPrincipal getAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("{user.not.authenticated}");
        }

        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            // Kimlik var ama marketplace kullanıcısı değil — neredeyse her zaman bir StaffPrincipal.
            // Bunu "kaynak bulunamadı" diye 404'e çevirmek teşhisi imkânsız kılıyordu: personel
            // kimliğiyle gelen bir /admin ucu, iş mantığına hiç girmeden "bulunamadı" diyordu ve
            // sebep hiçbir yerde yazmıyordu. Bu bir yapılandırma hatasıdır, kullanıcı hatası değil.
            throw new IllegalStateException(
                    "securityService.getAuthenticated* marketplace kullanıcısı bekliyor ama kimlik "
                            + authentication.getPrincipal().getClass().getSimpleName()
                            + ". /admin/** altındaki bir metot bunu çağıramaz — log satırında bile. "
                            + "Aktörü StaffPrincipal olarak parametreyle al.");
        }

        return principal;
    }

    @Override
    public UUID getAuthenticatedUserId() {
        return getAuthenticatedPrincipal().id();
    }

    @Override
    @Transactional(readOnly = true)
    public User getAuthenticatedUser() {
        UUID userId = getAuthenticatedUserId();
        return userDao.findById(userId).orElseThrow(() -> new ResourceNotFoundException("{user.not.found}"));
    }

    @Override
    @Transactional(readOnly = true)
    public User getSystemUser() {
        return userDao.findById(SystemUser.ID)
                .orElseThrow(() -> new ResourceNotFoundException("{system.user.not.found}"));
    }
}
