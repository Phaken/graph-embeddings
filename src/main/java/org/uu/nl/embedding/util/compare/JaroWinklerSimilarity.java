package org.uu.nl.embedding.util.compare;

public class JaroWinklerSimilarity implements Similarity<String> {

    @Override
    public boolean needsPreproces() {
        return false;
    }

    public void preProcess(String item) { }

    @Override
    public double calculate(String s1, String s2) {
        // lowest score on empty strings
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0;
        }
        // highest score on equal strings
        if (s1.equals(s2)) {
            return 1;
        }
        // some score on different strings
        int prefixMatch = 0; // exact prefix matches
        int matches = 0; // matches (including prefix and ones requiring transposition)
        int transpositions = 0; // matching characters that are not aligned but close together
        int maxLength = Math.max(s1.length(), s2.length());
        int maxMatchDistance = Math.max((int) Math.floor(maxLength / 2.0) - 1, 0); // look-ahead/-behind to limit transposed matches
        // comparison
        final String shorter = s1.length() < s2.length() ? s1 : s2;
        final String longer = s1.length() >= s2.length() ? s1 : s2;
        for (int i = 0; i < shorter.length(); i++) {
            // check for exact matches
            boolean match = shorter.charAt(i) == longer.charAt(i);
            if (match) {
                if (i < 4) {
                    // prefix match (of at most 4 characters, as described by the algorithm)
                    prefixMatch++;
                }
                matches++;
                continue;
            }
            // check for transposed matches
            for (int j = Math.max(i - maxMatchDistance, 0); j < Math.min(i + maxMatchDistance, longer.length()); j++) {
                if (i == j) {
                    // case already covered
                    continue;
                }
                // transposition required to match?
                match = shorter.charAt(i) == longer.charAt(j);
                if (match) {
                    transpositions++;
                    break;
                }
            }
        }
        // any matching characters?
        if (matches == 0) {
            return 0;
        }
        // modify transpositions (according to the algorithm)
        transpositions = (int) (transpositions / 2.0);
        // non prefix-boosted score
        double score = 1/3d * (matches / (double) longer.length() + matches / (double) shorter.length() + (matches - transpositions)
                / (double) matches);
        if (score < 0.7) {
            return score;
        }
        // we already have a good match, hence we boost the score proportional to the common prefix
        return score + prefixMatch * 0.1 * (1.0 - score);
    }
}
