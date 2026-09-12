package de.omegazirkel.risingworld.mail;

import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

public class MailSettingsTest {
    @Test
    public void emptyTrustedPluginSendersUseTheBundledDefaults() {
        Properties values = new Properties();
        values.setProperty("trustedPluginSenders", "");
        Properties defaults = new Properties();
        defaults.setProperty("trustedPluginSenders", "OZ - Bosses,OZ - Shop");

        assertTrue(MailSettings.trustedPluginSenders(values, defaults).contains("oz - bosses"));
        assertTrue(MailSettings.trustedPluginSenders(values, defaults).contains("oz - shop"));
    }
}
