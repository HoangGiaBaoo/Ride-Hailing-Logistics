package org.example.ride_hailing_logistics.trip.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Xep hang ung vien "rank-v1" (FR-MAT-003): gan diem don hon dung truoc; bang khoang cach thi vi tri moi hon dung truoc.
 * Tieu chi minh bach va co phien ban: doi tieu chi = doi ten version trong cau hinh.
 */
public final class CandidateRanker {

    private CandidateRanker() {
    }

    public static List<Candidate> rank(Collection<Candidate> candidates, Set<UUID> excludedDrivers) {
        return candidates.stream()
                .filter(c -> !excludedDrivers.contains(c.driverId()))
                .sorted(Comparator.comparingDouble(Candidate::distanceMeters)
                        .thenComparingLong(Candidate::locationAgeMillis)
                        .thenComparing(Candidate::driverId))
                .toList();
    }

    public record Candidate(UUID driverId, double distanceMeters, long locationAgeMillis) {}
}
