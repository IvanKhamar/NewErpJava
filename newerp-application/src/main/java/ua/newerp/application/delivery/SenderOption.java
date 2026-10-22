package ua.newerp.application.delivery;

/** A sender from the settings (nova-poshta.senders). Its API key never gets here, it stays on the server. */
public record SenderOption(String id, String name, boolean isDefault) {
}
