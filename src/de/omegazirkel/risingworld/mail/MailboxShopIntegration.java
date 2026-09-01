package de.omegazirkel.risingworld.mail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.risingworld.api.Plugin;
import net.risingworld.api.objects.Player;
import de.omegazirkel.risingworld.tools.I18n;

/** Reflection-only OZ Shop integration keeps OZMail independently loadable. */
public final class MailboxShopIntegration {
    private static final String OFFER_ID = "ozmail.extra-mailbox";
    private final Plugin owner;
    private final MailboxCapacityService capacity;

    public MailboxShopIntegration(Plugin owner, MailboxCapacityService capacity) {
        this.owner = owner;
        this.capacity = capacity;
    }

    public void register(MailSettings settings) {
        Plugin shop = owner.getPluginByName("OZ - Shop");
        if (shop == null) return;
        try {
            if (!settings.enableExtraMailboxShopOffer) {
                shop.getClass().getMethod("unregisterOffer", String.class, String.class)
                        .invoke(shop, OFFER_ID, "OZ - Mail");
                return;
            }
            Class<?> callbackType = Class.forName("de.omegazirkel.risingworld.shop.ShopPurchaseCallback");
            Class<?> priceType = Class.forName("de.omegazirkel.risingworld.shop.ShopPriceResolver");
            Class<?> localizationType = Class.forName("de.omegazirkel.risingworld.shop.ShopOfferLocalization");
            Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[] { callbackType }, callback());
            Object price = Proxy.newProxyInstance(priceType.getClassLoader(), new Class<?>[] { priceType }, price(settings));
            Object localization = Proxy.newProxyInstance(localizationType.getClassLoader(),
                    new Class<?>[] { localizationType }, localization());
            Method register = shop.getClass().getMethod("registerOffer", String.class, String.class, String.class,
                    long.class, String.class, String.class, String.class, callbackType, priceType, localizationType);
            register.invoke(shop, OFFER_ID, "Extra mailbox", "Adds one full mailbox capacity.",
                    settings.extraMailboxBasePrice, "", "extra-mailbox", "OZ - Mail", callback, price, localization);
        } catch (ReflectiveOperationException ex) {
            // Shop is optional; registration retries after a settings reload.
        }
    }

    private InvocationHandler localization() {
        I18n translations = I18n.getInstance(owner);
        return (proxy, method, args) -> {
            Player player = args != null && args.length > 0 && args[0] instanceof Player p ? p : null;
            return switch (method.getName()) {
                case "title" -> translations.get("mail.shop.extra.mailbox.title", player);
                case "description" -> translations.get("mail.shop.extra.mailbox.desc", player);
                default -> objectMethod(proxy, method);
            };
        };
    }

    private InvocationHandler price(MailSettings settings) {
        return (proxy, method, args) -> {
            if (!"price".equals(method.getName())) return objectMethod(proxy, method);
            Player player = args != null && args.length > 0 && args[0] instanceof Player p ? p : null;
            int purchased = capacity == null ? 0 : capacity.extraMailboxes(player);
            double raw = settings.extraMailboxBasePrice * Math.pow(settings.extraMailboxPriceIncreaseFactor, purchased);
            return Math.max(0L, Math.min(Long.MAX_VALUE, Math.round(raw)));
        };
    }

    private InvocationHandler callback() {
        return (proxy, method, args) -> {
            if (!"complete".equals(method.getName())) return objectMethod(proxy, method);
            Player player = args != null && args.length > 0 && args[0] instanceof Player p ? p : null;
            Object offer = args != null && args.length > 1 ? args[1] : null;
            int total = capacity == null ? -1 : capacity.addExtraMailbox(player);
            return purchaseResult(total >= 0, total >= 0
                    ? "Extra mailbox purchased. Total: " + total
                    : "Could not persist extra mailbox purchase.", offer);
        };
    }

    private Object purchaseResult(boolean success, String message, Object offer) {
        try {
            Class<?> result = Class.forName("de.omegazirkel.risingworld.shop.ShopPurchaseResult");
            if (success) {
                Class<?> offerType = Class.forName("de.omegazirkel.risingworld.shop.ShopOffer");
                return result.getMethod("success", String.class, offerType).invoke(null, message, offer);
            }
            Class<?> code = Class.forName("de.omegazirkel.risingworld.shop.ShopErrorCode");
            Object callbackFailed = Enum.valueOf(code.asSubclass(Enum.class), "CALLBACK_FAILED");
            return result.getMethod("failure", code, String.class).invoke(null, callbackFailed, message);
        } catch (ReflectiveOperationException ex) { return null; }
    }

    private Object objectMethod(Object proxy, Method method) {
        return switch (method.getName()) {
            case "toString" -> "OZMailExtraMailboxShopProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> false;
            default -> null;
        };
    }
}
