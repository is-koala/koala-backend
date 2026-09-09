package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceDao extends JpaRepository<Device, UUID> {

    List<Device> findAllByUser_IdAndPushTokenIsNotNull(UUID userId);

    List<Device> findAllByPushToken(String pushToken);

    Optional<Device> findByUserAndNameAndType(User user, String name, DeviceType type);

    Optional<Device> findByUser_IdAndClientDeviceId(UUID userId, String clientDeviceId);

    Optional<Device> findByIdAndUser_IdAndPushTokenIsNotNull(UUID id, UUID userId);

    @Modifying
    @Query("update Device d set d.pushToken = null where d.id = :deviceId and d.pushToken = :expectedToken")
    int clearPushTokenIfMatches(@Param("deviceId") UUID deviceId,
                                @Param("expectedToken") String expectedToken);
}
