package com.iflytek.skillhub.search;

import java.util.Locale;
import java.util.regex.Pattern;

/** Matches English words at boundaries while retaining substring matching for unspaced Chinese. */
final class SearchTermMatcher {
    private SearchTermMatcher() { }

    static boolean contains(String text, String term) {
        if (term.isBlank()) return false;
        if (term.chars().allMatch(c -> c < 128) && term.chars().anyMatch(Character::isLetterOrDigit)) {
            return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])")
                    .matcher(text).find();
        }
        return text.contains(term);
    }

    /** Bounded edit distance is only used for product identity fields, never arbitrary documentation. */
    static boolean similarIdentityWord(String identity, String term) {
        if (term.length() < 4 || !term.matches("[a-z]+")) return false;
        for (String word : identity.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() < 4 || Math.abs(word.length() - term.length()) > 2) continue;
            int distance = distance(word, term);
            if (distance <= (Math.max(word.length(), term.length()) >= 6 ? 2 : 1)) return true;
        }
        return false;
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return previous[right.length()];
    }
}
