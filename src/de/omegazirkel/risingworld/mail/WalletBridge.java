package de.omegazirkel.risingworld.mail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.risingworld.api.Plugin;

/** Optional Wallet v1 bridge. COD calls only its atomic idempotent transfer. */
public final class WalletBridge {
    private final Plugin owner;

    public WalletBridge(Plugin owner) {
        this.owner = owner;
    }

    public TransferResult transferIdempotent(int payerDbId, int payeeDbId, long amount, String reason,
            String currencyIdentifier, String correlationId) {
        Plugin wallet = owner == null ? null : owner.getPluginByName("OZ - Wallet");
        if (wallet == null) return TransferResult.unavailable();
        try {
            Method method = wallet.getClass().getMethod("transferIdempotent", int.class, int.class, long.class,
                    String.class, String.class, String.class, String.class);
            Object result = method.invoke(wallet, payerDbId, payeeDbId, amount, reason, currencyIdentifier,
                    "OZ - Mail", correlationId);
            return TransferResult.from(result);
        } catch (ReflectiveOperationException ex) {
            return TransferResult.unavailable();
        }
    }

    public String defaultCurrencyIdentifier() {
        Plugin wallet = owner == null ? null : owner.getPluginByName("OZ - Wallet");
        if (wallet == null) return "";
        try {
            Object result = wallet.getClass().getMethod("defaultCurrencyIdentifier").invoke(wallet);
            return result instanceof String value ? value.trim() : "";
        } catch (ReflectiveOperationException ex) {
            return "";
        }
    }

    /** Lists registered Wallet currency identifiers; unavailable or legacy Wallets return an empty list. */
    public List<String> currencyIdentifiers() {
        Plugin wallet = owner == null ? null : owner.getPluginByName("OZ - Wallet");
        if (wallet == null) return List.of();
        try {
            Object result = wallet.getClass().getMethod("listCurrencies").invoke(wallet);
            if (!Boolean.TRUE.equals(readField(result, "success"))) return List.of();
            Object currencies = readField(result, "currencies");
            if (!(currencies instanceof Iterable<?> iterable)) return List.of();
            List<String> identifiers = new ArrayList<>();
            for (Object currency : iterable) {
                Object identifier = currency == null ? null
                        : currency.getClass().getMethod("getIdentifier").invoke(currency);
                if (identifier instanceof String value && !value.isBlank()) identifiers.add(value.trim());
            }
            return List.copyOf(identifiers);
        } catch (ReflectiveOperationException ex) {
            return List.of();
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public record TransferResult(boolean success, String errorCode, String message) {
        static TransferResult unavailable() { return new TransferResult(false, "DATABASE_ERROR", "Wallet unavailable"); }
        static TransferResult from(Object result) {
            return new TransferResult(Boolean.TRUE.equals(field(result, "success")), string(field(result, "errorCode")),
                    string(field(result, "message")));
        }
        private static Object field(Object target, String name) {
            return readField(target, name);
        }
        private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    }
}
