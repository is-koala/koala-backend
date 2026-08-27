package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppSettingDao extends JpaRepository<AppSetting, UUID> {

    Optional<AppSetting> findByKey(String key);

    List<AppSetting> findAllByOrderByKeyAsc();
}
