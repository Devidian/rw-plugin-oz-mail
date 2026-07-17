package de.omegazirkel.risingworld.mail;

import java.lang.reflect.Method;

import net.risingworld.api.Plugin;

/**
 * Optional consumer-side bridge for text-only plugin mail. The server operator
 * must list the sender plugin in OZMail's trustedPluginSenders setting.
 */
public final class MailBridge {
    public static final int API_VERSION = 1;
    private final Plugin owner;

    public MailBridge(Plugin owner) {
        this.owner = owner;
    }

    public BridgeResult sendTextMail(PluginMailRequest request) {
        if (owner == null || request == null || !request.valid()) return BridgeResult.invalid();
        String callerPlugin = owner.getDescription("name");
        if (callerPlugin == null || callerPlugin.isBlank()
                || !callerPlugin.trim().equalsIgnoreCase(request.senderPlugin().trim())) {
            return BridgeResult.invalid();
        }
        Plugin mailPlugin = owner.getPluginByName("OZ - Mail");
        if (mailPlugin == null) return BridgeResult.unavailable();
        try {
            Method method = mailPlugin.getClass().getMethod("sendPluginMail", String.class, int.class,
                    String.class, String.class, String.class, String.class);
            Object result = method.invoke(mailPlugin, callerPlugin.trim(), request.recipientDbId(),
                    request.recipientName(), request.subject(), request.body(), request.correlationId());
            return BridgeResult.from(result);
        } catch (ReflectiveOperationException ex) {
            return BridgeResult.unavailable();
        }
    }

    public record PluginMailRequest(String senderPlugin, int recipientDbId, String recipientName, String subject,
            String body, String correlationId) {
        public boolean valid() {
            return senderPlugin != null && !senderPlugin.isBlank() && recipientDbId > 0
                    && recipientName != null && !recipientName.isBlank() && subject != null && !subject.isBlank()
                    && body != null;
        }
    }

    public record BridgeResult(String code, boolean success, boolean reconciliationRequired, String mailId,
            String correlationId) {
        static BridgeResult unavailable() { return new BridgeResult("MAIL_UNAVAILABLE", false, false, "", ""); }
        static BridgeResult invalid() { return new BridgeResult("INVALID_REQUEST", false, false, "", ""); }

        static BridgeResult from(Object result) {
            return new BridgeResult(string(result, "code"), bool(result, "success"),
                    bool(result, "reconciliationRequired"), string(result, "mailId"), string(result, "correlationId"));
        }

        private static String string(Object target, String methodName) {
            Object value = invoke(target, methodName);
            return value == null ? "" : String.valueOf(value);
        }

        private static boolean bool(Object target, String methodName) {
            return Boolean.TRUE.equals(invoke(target, methodName));
        }

        private static Object invoke(Object target, String methodName) {
            if (target == null) return null;
            try { return target.getClass().getMethod(methodName).invoke(target); }
            catch (ReflectiveOperationException ex) { return null; }
        }
    }
}
