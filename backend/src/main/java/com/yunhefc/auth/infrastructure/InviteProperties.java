package com.yunhefc.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yunhefc.invite")
public class InviteProperties {

    private String expiryCron = "0 0 3 * * ?";
    private String zone = "Asia/Shanghai";

    public String getExpiryCron() {
        return expiryCron;
    }

    public void setExpiryCron(String expiryCron) {
        this.expiryCron = expiryCron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }
}
