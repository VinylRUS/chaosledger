package dev.chaosledger.chaosledger.economy;

/**
 * Russian pluralisation + formatting for the «медяк» currency.
 *
 * <p>Russian nouns change their ending based on the count they refer to:</p>
 * <ul>
 *   <li>1, 21, 31, … → «медяк» (nominative singular)</li>
 *   <li>2–4, 22–24, 32–34, … → «медяка» (genitive singular)</li>
 *   <li>0, 5–19, 25–29, … → «медяков» (genitive plural)</li>
 * </ul>
 *
 * <p>The same rule is reused for any other currency whose display name follows
 * the same pattern; the helper picks the form by the integer part of the
 * amount. Non-integer amounts (e.g. {@code 1.5}) use the genitive plural form,
 * matching the spoken convention («полтора медяка»).</p>
 */
public final class MoneyFormat {

    private MoneyFormat() {}

    /** Default currency display name (singular nominative). */
    public static final String COPPER_SINGULAR = "медяк";
    public static final String COPPER_FEW       = "медяка";   // 2-4
    public static final String COPPER_MANY      = "медяков";  // 0, 5-19

    /**
     * Pick the right form of «медяк» for the given amount.
     *
     * @param amount the money amount (may be fractional)
     * @return one of {@link #COPPER_SINGULAR}, {@link #COPPER_FEW}, {@link #COPPER_MANY}
     */
    public static String copperForm(double amount) {
        // Fractional amounts always use genitive plural («1.5 медяков» is the
        // common colloquial form; strict literary Russian would say «полтора
        // медяка», but that requires knowing «полтора» vs «полторы» etc. —
        // out of scope). The integer rule is applied only to whole numbers.
        if (amount != Math.floor(amount)) {
            return COPPER_MANY;
        }
        long n = Math.abs((long) amount);
        long mod10 = n % 10;
        long mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return COPPER_SINGULAR;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
            return COPPER_FEW;
        }
        return COPPER_MANY;
    }

    /**
     * Format an amount + «медяк» form into a single string.
     *
     * <p>The amount is rendered without a trailing {@code .0} when it is whole
     * (e.g. {@code 10} not {@code 10.0}), so the chat reads naturally:
     * «10 медяков», «1 медяк», «3 медяка».</p>
     *
     * @param amount the money amount
     * @return formatted string, e.g. {@code "10 медяков"}
     */
    public static String copper(double amount) {
        String num;
        if (amount == Math.floor(amount)) {
            num = Long.toString((long) amount);
        } else {
            num = Double.toString(amount);
        }
        return num + " " + copperForm(amount);
    }
}
