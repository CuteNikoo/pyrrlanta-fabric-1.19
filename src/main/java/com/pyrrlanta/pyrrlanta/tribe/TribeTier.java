package com.pyrrlanta.pyrrlanta.tribe;

// A tribe's progression tier, derived purely from its total member count and claim count
// (both must meet the threshold). Ordinal + 1 is the tier number (1-5). Each tier grants a
// force-load allowance and a passive; see TribeTierEffects for how the passives are applied.
public enum TribeTier {
    ENCAMPMENT("Encampment", 1, 0, 0,
            "No passive."),
    OUTPOST("Outpost", 2, 25, 1,
            "Hearth: Regeneration I inside your own claims, suppressed for 10s after taking damage."),
    SETTLEMENT("Settlement", 3, 60, 3,
            "Pack Instinct: Haste I while within 16 blocks of a tribemate, anywhere."),
    COMMUNE("Commune", 4, 120, 6,
            "Long Watch: tribemates within 128 blocks are outlined through terrain; death coordinates broadcast to tribe chat."),
    ASCENSION("Ascension", 5, 200, 10,
            "Enduring: keep your XP levels on death, anywhere.");

    private final String displayName;
    private final int minMembers;
    private final int minChunks;
    private final int forceLoadLimit;
    private final String passive;

    TribeTier(String displayName, int minMembers, int minChunks, int forceLoadLimit, String passive) {
        this.displayName = displayName;
        this.minMembers = minMembers;
        this.minChunks = minChunks;
        this.forceLoadLimit = forceLoadLimit;
        this.passive = passive;
    }

    public String displayName() {
        return displayName;
    }

    public int number() {
        return ordinal() + 1;
    }

    public int minMembers() {
        return minMembers;
    }

    public int minChunks() {
        return minChunks;
    }

    public int forceLoadLimit() {
        return forceLoadLimit;
    }

    public String passive() {
        return passive;
    }

    // The highest tier whose member and claim thresholds the tribe currently meets. Since the
    // thresholds are monotonic, this walks from the top down and returns the first match;
    // ENCAMPMENT always qualifies (a tribe always has at least its leader).
    public static TribeTier of(Tribe tribe) {
        int members = tribe.getMembers().size();
        int chunks = tribe.getClaims().size();
        TribeTier[] tiers = values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            TribeTier tier = tiers[i];
            if (members >= tier.minMembers && chunks >= tier.minChunks) {
                return tier;
            }
        }
        return ENCAMPMENT;
    }

    // The next tier up from this one, or null if already at the top -- used to show players
    // what they're working toward.
    public TribeTier next() {
        TribeTier[] tiers = values();
        return ordinal() + 1 < tiers.length ? tiers[ordinal() + 1] : null;
    }
}
