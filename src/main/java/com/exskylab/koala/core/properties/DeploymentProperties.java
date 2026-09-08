package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class DeploymentProperties {

    public enum Deployment { LOCAL, STAGING, PRODUCTION }

    private Deployment deployment = Deployment.LOCAL;

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment == null ? Deployment.LOCAL : deployment;
    }

    public boolean isProduction() {
        return deployment == Deployment.PRODUCTION;
    }
}
