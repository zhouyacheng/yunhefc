package com.yunhefc.match.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.contracts.event.DerivedScoreSnapshot;
import com.yunhefc.contracts.match.MatchSummarySnapshot;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchType;
import com.yunhefc.match.domain.OurSide;
import com.yunhefc.match.domain.TeamMatch;
import org.junit.jupiter.api.Test;

class MatchSummaryMappingTest {

    @Test
    void derivedScoreOverridesStoredScore() {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(
                match(MatchStatus.FINISHED, OurSide.HOME, 3, 2),
                new DerivedScoreSnapshot(1, 0));

        assertThat(summary.scoreHome()).isEqualTo(1);
        assertThat(summary.scoreAway()).isZero();
        assertThat(summary.resultText()).isEqualTo("胜");
    }

    @Test
    void fallsBackToStoredScoreWithoutDerivedScore() {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(
                match(MatchStatus.FINISHED, OurSide.HOME, 0, 1), null);

        assertThat(summary.scoreHome()).isZero();
        assertThat(summary.scoreAway()).isEqualTo(1);
        assertThat(summary.resultText()).isEqualTo("负");
    }

    @Test
    void returnsNullScoreAndResultWhenBothSourcesEmpty() {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(
                match(MatchStatus.FINISHED, OurSide.HOME, null, null), null);

        assertThat(summary.scoreHome()).isNull();
        assertThat(summary.scoreAway()).isNull();
        assertThat(summary.resultText()).isNull();
    }

    @Test
    void internalMatchHasNoResultText() {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(
                match(MatchStatus.FINISHED, null, 2, 1), null);

        assertThat(summary.scoreHome()).isEqualTo(2);
        assertThat(summary.scoreAway()).isEqualTo(1);
        assertThat(summary.resultText()).isNull();
    }

    @Test
    void unfinishedMatchHasNoResultText() {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(
                match(MatchStatus.IN_PROGRESS, OurSide.HOME, 2, 1),
                new DerivedScoreSnapshot(1, 0));

        assertThat(summary.scoreHome()).isEqualTo(1);
        assertThat(summary.resultText()).isNull();
    }

    @Test
    void copiesQueueCapacitiesForCardRendering() {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(
                match(MatchStatus.SIGNUP_OPEN, null, null, null), null);

        assertThat(summary.capacity()).isEqualTo(30);
        assertThat(summary.guestCapacity()).isEqualTo(4);
        assertThat(summary.waitlistCapacity()).isEqualTo(2);
    }

    private static TeamMatch match(MatchStatus status, OurSide ourSide, Integer home, Integer away) {
        TeamMatch match = new TeamMatch();
        match.setId(1L);
        match.setTeamId(1L);
        match.setTitle("测试比赛");
        match.setType(MatchType.INTERNAL);
        match.setStatus(status);
        match.setOurSide(ourSide);
        match.setHomeTeamName("主队");
        match.setAwayTeamName("客队");
        match.setSeason("2026");
        match.setCapacity(30);
        match.setGuestCapacity(4);
        match.setWaitlistCapacity(2);
        match.setScoreHome(home);
        match.setScoreAway(away);
        return match;
    }
}
