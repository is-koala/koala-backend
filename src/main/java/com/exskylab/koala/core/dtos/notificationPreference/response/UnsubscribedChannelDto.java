package com.exskylab.koala.core.dtos.notificationPreference.response;

import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;

public record UnsubscribedChannelDto(
        @Schema(description = "Bağlantının kapattığı bildirim kategorisi; token yalnızca bunu kapatabilir.")
        NotificationCategory category,
        @Schema(description = "Bağlantının kapattığı kanal; token başka hiçbir kanala dokunamaz.")
        NotificationChannel channel,
        @Schema(description = "Kanal bu istekten önce zaten kapalıysa true. Aynı bağlantıya ikinci tık hata "
                + "değildir: yanıt aynı kategori ve kanalı taşır, hiçbir tercih satırı ya da rıza kaydı yeniden "
                + "yazılmaz.")
        boolean alreadyDisabled) {
}
