package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.DeploymentProperties;
import com.exskylab.koala.core.properties.EmailDeliveryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MailDeliveryEnvironmentGuard implements InitializingBean {

    public static final String MARKER = "MAIL_DELIVERY_GUARD";

    private final DeploymentProperties deploymentProperties;

    private final EmailDeliveryProperties deliveryProperties;

    public MailDeliveryEnvironmentGuard(DeploymentProperties deploymentProperties,
                                        EmailDeliveryProperties deliveryProperties) {
        this.deploymentProperties = deploymentProperties;
        this.deliveryProperties = deliveryProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (deploymentProperties.getDeployment() == DeploymentProperties.Deployment.LOCAL) {
            return;
        }

        if (deliveryProperties.getMode() == EmailDeliveryProperties.Mode.UNRESTRICTED) {
            if (deploymentProperties.getDeployment() == DeploymentProperties.Deployment.STAGING) {
                throw new IllegalStateException(
                        "MAIL_DELIVERY_MODE must be ALLOWLIST in staging; UNRESTRICTED would deliver marketplace mail without a recipient gate");
            }
            log.warn("{}: MAIL_DELIVERY_MODE is UNRESTRICTED in production; launch-cutover still records SMTP_UNRESTRICTED_APPROVAL_ID before public traffic",
                    MARKER);
            return;
        }

        if (deliveryProperties.getAllowedRecipients().isEmpty()) {
            log.warn("{}: MAIL_ALLOWED_RECIPIENTS is empty so every message is suppressed", MARKER);
        }
    }
}
