/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

/**
 * Server-side translation of tooltip text (PAR-INFRA-05): {@code dev.tfp.paper.i18n.Messages#get(String, String,
 * Object...)} in production. A vanilla client cannot resolve our keys, so the text is translated for the client's
 * language before it is put into the packet. Thread-safe.
 */
@FunctionalInterface
public interface TooltipText {
    /** The message for {@code key} in {@code locale} (a code such as {@code tr_tr}) with {@code args} filled in. */
    String get(String locale, String key, Object... args);
}
