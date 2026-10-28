package org.example.ride_hailing_logistics.trip.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** rank-v1 (FR-MAT-003): khoang cach tang dan; bang nhau thi vi tri moi hon truoc; loai tai xe da duoc moi. */
class CandidateRankerTest {

    private final UUID far = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID nearStale = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID nearFresh = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID declined = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void closerDriverComesFirstAndFresherBreaksTies() {
        List<CandidateRanker.Candidate> ranked = CandidateRanker.rank(List.of(
                new CandidateRanker.Candidate(far, 500, 1_000),
                new CandidateRanker.Candidate(nearStale, 300, 2_000),
                new CandidateRanker.Candidate(nearFresh, 300, 500)), Set.of());
        assertEquals(List.of(nearFresh, nearStale, far), ranked.stream().map(CandidateRanker.Candidate::driverId).toList());
    }

    @Test
    void driversAlreadyOfferedForThisTripAreExcluded() {
        // FR-MAT-013: tu choi / het han roi thi khong moi lai cung chuyen
        List<CandidateRanker.Candidate> ranked = CandidateRanker.rank(List.of(
                new CandidateRanker.Candidate(declined, 100, 100),
                new CandidateRanker.Candidate(far, 500, 100)), Set.of(declined));
        assertEquals(1, ranked.size());
        assertEquals(far, ranked.get(0).driverId());
    }

    @Test
    void emptyInputGivesEmptyRanking() {
        assertTrue(CandidateRanker.rank(List.of(), Set.of()).isEmpty());
    }
}
