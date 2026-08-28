package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaDao extends JpaRepository<Media, UUID> {

    Optional<Media> findByFileUrl(String fileUrl);
}
