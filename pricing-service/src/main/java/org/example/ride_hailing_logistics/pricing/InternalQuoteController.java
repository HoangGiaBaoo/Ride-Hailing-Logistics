package org.example.ride_hailing_logistics.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteSnapshot;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** API noi bo cho trip-service: dung / tra bao gia khi tao chuyen. */
@RestController
@RequestMapping("/internal/quotes")
@RequiredArgsConstructor
public class InternalQuoteController {

    private final QuoteService quoteService;

    @PostMapping("/{quoteId}/consume")
    public ApiResponse<QuoteSnapshot> consume(@PathVariable UUID quoteId, @Valid @RequestBody ConsumeCommand cmd) {
        return ApiResponse.ok(quoteService.consumeQuote(quoteId, cmd.customerId(), cmd.tripId()));
    }

    @PostMapping("/{quoteId}/release")
    public ApiResponse<Map<String, String>> release(@PathVariable UUID quoteId, @Valid @RequestBody ReleaseCommand cmd) {
        quoteService.releaseQuote(quoteId, cmd.tripId());
        return ApiResponse.ok(Map.of("status", "RELEASED"));
    }

    public record ConsumeCommand(@NotNull UUID customerId, @NotNull UUID tripId) {}

    public record ReleaseCommand(@NotNull UUID tripId) {}
}
