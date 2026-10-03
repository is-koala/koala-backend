package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.TimesheetProperties;
import com.exskylab.koala.core.properties.TransportBoardingProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class BoardingLateSyncGuard implements InitializingBean {

    private final TransportBoardingProperties boardingProperties;

    private final TimesheetProperties timesheetProperties;

    public BoardingLateSyncGuard(TransportBoardingProperties boardingProperties,
                                 TimesheetProperties timesheetProperties) {
        this.boardingProperties = boardingProperties;
        this.timesheetProperties = timesheetProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (boardingProperties.getLateSyncMax().compareTo(timesheetProperties.getMaxOfflineAge()) > 0) {
            throw new IllegalStateException("transport.boarding.late-sync-max (" + boardingProperties.getLateSyncMax()
                    + ") timesheet.max-offline-age (" + timesheetProperties.getMaxOfflineAge() + ") degerini asamaz");
        }
    }
}
