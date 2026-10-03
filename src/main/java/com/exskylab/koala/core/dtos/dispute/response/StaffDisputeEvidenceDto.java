package com.exskylab.koala.core.dtos.dispute.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.EvidenceUploaderSide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record StaffDisputeEvidenceDto(UUID id, UUID disputeId, SafeUserDto uploadedBy,
                                      EvidenceUploaderSide uploaderSide, String fileName,
                                      String contentType, long fileSize, String checksumSha256,
                                      Instant createdAt,
                                      @Schema(types = {"string", "null"}, format = "date-time",
                                              description = "Görselin EXIF/GPS ve metin parçalarından arındırıldığı an; "
                                                      + "yüklemede temizlenen görselde yükleme anı, PDF'te null")
                                      Instant metadataScrubbedAt,
                                      @Schema(types = {"string", "null"},
                                              description = "Saklanan görsel sonradan temizlendiyse temizlikten önceki "
                                                      + "baytların SHA-256'sı; checksumSha256 temizlenmiş baytlarınki")
                                      String preScrubChecksumSha256) {
}
