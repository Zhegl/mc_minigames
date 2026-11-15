package me.petr1furious.potatominigames.games.party;

record PartyRoundOptions(
        boolean embedded,
        int countdownSeconds,
        int armsRaceSeconds,
        int minersPreparationSeconds,
        int minersCombatSeconds,
        int minersFinaleSeconds) {
    static PartyRoundOptions party() {
        return new PartyRoundOptions(true, 4, 180, 25, 120, 60);
    }

    PartyRoundOptions forRound(int index) {
        // Intermission happens before arena setup; every arena still needs its spawn countdown.
        return new PartyRoundOptions(
                embedded,
                countdownSeconds,
                armsRaceSeconds,
                minersPreparationSeconds,
                minersCombatSeconds,
                minersFinaleSeconds);
    }

    static PartyRoundOptions standalone() {
        return new PartyRoundOptions(false, 5, 600, 25, 180, 120);
    }
}
