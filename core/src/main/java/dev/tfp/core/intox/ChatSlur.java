/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onServerChat, ALPHABET)
 */
package dev.tfp.core.intox;

/**
 * TFC's drunk chat (PAR-SURV-08): every word of a message may get two letters swapped, a letter repeated one to three
 * times and a letter replaced by a random one, each with the slur chance ({@link Intoxication#slurChance}). The order of
 * random draws is TFC's, so the same random sequence gives TFC's result. Pure; thread-safe given its own random.
 *
 * <p>TFC's quirks are kept: the message is split on single spaces with Java's {@code split(" ")}, which drops trailing
 * empty words (so trailing spaces are lost), empty words between double spaces stay; the case of the replacement letter
 * follows a <em>randomly chosen</em> letter of the word, not the replaced one.
 */
public final class ChatSlur {
    /** TFC {@code ForgeEventHandler.ALPHABET}. */
    static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    /** TFC's {@code RandomSource} calls this uses. */
    public interface Random {
        /** A float in [0, 1). */
        float nextFloat();

        /** An int in [0, bound). */
        int nextInt(int bound);
    }

    private ChatSlur() {
    }

    /** Whether a message at {@code level} is changed at all (TFC: the chance is above 0). */
    public static boolean slurs(float level) {
        return Intoxication.slurChance(level) > 0;
    }

    /**
     * TFC {@code onServerChat}: the message as sent at intoxication {@code level}; unchanged (no draws) at a level of 0.2
     * or less.
     */
    public static String slur(String message, float level, Random random) {
        float chance = Intoxication.slurChance(level);
        if (!(chance > 0)) {
            return message;
        }
        String[] words = message.split(" ");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }

            // Swap two letters (the float is drawn even for a one-letter word)
            if (random.nextFloat() < chance && word.length() >= 2) {
                int pos = random.nextInt(word.length() - 1);
                word = word.substring(0, pos) + word.charAt(pos + 1) + word.charAt(pos) + word.substring(pos + 2);
            }

            // Repeat / slur letters
            if (random.nextFloat() < chance) {
                int pos = random.nextInt(word.length());
                char repeat = word.charAt(pos);
                int amount = 1 + random.nextInt(3);
                word = word.substring(0, pos) + String.valueOf(repeat).repeat(amount)
                        + (pos + 1 < word.length() ? word.substring(pos + 1) : "");
            }

            // Add additional letters (TFC replaces the letter at pos)
            if (random.nextFloat() < chance) {
                int pos = random.nextInt(word.length());
                char replacement = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
                if (Character.isUpperCase(word.charAt(random.nextInt(word.length())))) {
                    replacement = Character.toUpperCase(replacement);
                }
                word = word.substring(0, pos) + replacement + (pos + 1 < word.length() ? word.substring(pos + 1) : "");
            }

            words[i] = word;
        }
        return String.join(" ", words);
    }
}
