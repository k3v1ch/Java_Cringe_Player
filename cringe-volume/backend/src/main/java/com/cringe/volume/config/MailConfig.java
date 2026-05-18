package com.cringe.volume.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailConfig {

    @Value("${receipt.from-email:}")
    private String fromEmail;

    @Value("${receipt.from-name:Cringe Volume Player}")
    private String fromName;

    public String getFromEmail() { return fromEmail; }
    public String getFromName() { return fromName; }

    public boolean isConfigured() {
        return fromEmail != null && !fromEmail.isBlank();
    }
}
