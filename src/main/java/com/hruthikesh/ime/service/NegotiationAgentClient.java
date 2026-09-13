package com.hruthikesh.ime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hruthikesh.ime.entity.enums.AgentRole;
import com.hruthikesh.ime.entity.enums.NegotiationStatus;
import com.hruthikesh.ime.entity.enums.TurnDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NegotiationAgentClient {

    private static final URI GROQ_CHAT_URL = URI.create("https://api.groq.com/openai/v1/chat/completions");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String model;

    @Value("${groq.max-output-tokens:256}")
    private int maxOutputTokens;

    @Autowired
    public NegotiationAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public record TurnHistoryEntry(String roleLabel, String message, BigDecimal price, BigDecimal quantity) {}

    public record NegotiationContext(
            AgentRole agentRole,
            String ownOrgName,
            String counterpartOrgName,
            String itemName,
            String itemDescription,
            String categoryName,
            BigDecimal ownTargetPrice,
            BigDecimal ownTargetQuantity,
            String unit,
            BigDecimal counterpartLastPrice,
            BigDecimal counterpartLastQuantity,
            String counterpartLastMessage,
            int roundNumber,
            int maxRounds,
            double distanceKm,
            List<TurnHistoryEntry> turnHistory
    ) {}

    public record AgentTurnResult(
            String message,
            BigDecimal proposedPricePerUnit,
            BigDecimal proposedQuantity,
            TurnDecision decision
    ) {}

    public record ReportContext(
            String forOrgName,
            String counterpartOrgName,
            String itemName,
            NegotiationStatus outcome,
            BigDecimal agreedPrice,
            BigDecimal agreedQuantity,
            String unit,
            String failureReason,
            List<TurnHistoryEntry> turnHistory
    ) {}

    public record ReportResult(String summary, String recommendation, String dealTerms) {}

    public AgentTurnResult generateTurn(NegotiationContext context) {
        String systemPrompt = buildTurnSystemPrompt(context);
        String userPrompt = buildTurnUserPrompt(context);
        String raw = callGroq(systemPrompt, userPrompt, true);
        try {
            return parseTurnJson(raw);
        } catch (JsonProcessingException first) {
            String retryRaw = callGroq(systemPrompt, userPrompt, true);
            try {
                return parseTurnJson(retryRaw);
            } catch (JsonProcessingException second) {
                return fallbackTurn(context, readableModelText(retryRaw));
            }
        }
    }

    public ReportResult generateReport(ReportContext context) {
        String systemPrompt = """
                You are a B2B deal analyst writing a negotiation report for one organisation.
                All monetary values are in Indian rupees (INR). Use the ₹ symbol for prices.
                Return ONLY valid JSON with exactly these keys:
                {"summary":"string","recommendation":"string","dealTerms":"string or null"}
                Write from the perspective of the organisation named in the user message.
                Be concise, professional, and actionable.
                """;
                try {
            String userPrompt = buildReportUserPrompt(context);
            String raw = callGroq(systemPrompt, userPrompt, true);
            try {
                return parseReportJson(raw);
            } catch (JsonProcessingException first) {
                return parseReportJson(callGroq(systemPrompt, userPrompt, true));
            }
        } catch (Exception e) {
            return fallbackReport(context);
        }
    }

    private String buildTurnSystemPrompt(NegotiationContext ctx) {
        String roleGoal = ctx.agentRole() == AgentRole.SUPPLIER_AGENT
                ? "You represent the SUPPLIER. Maximize price while staying reasonable. Never agree below your listed supply price unless within 5%% tolerance. Do not offer more quantity than listed."
                : "You represent the DEMANDER. Minimize price while staying reasonable. Never agree above your listed budget unless within 5%% tolerance. Do not request more quantity than listed.";

        return """
                You are an AI negotiation agent in an industrial materials marketplace.
                All monetary values are in Indian rupees (INR). Use the ₹ symbol for prices.
                %s
                Return ONLY valid JSON with exactly these keys:
                {"message":"string","proposedPricePerUnit":number,"proposedQuantity":number,"decision":"CONTINUE|ACCEPT|REJECT"}
                Rules:
                - Be professional and concise in message.
                - Converge within %d rounds.
                - Use ACCEPT when price is within 5%% and quantity fits both listings.
                - Use REJECT only if no deal is possible.
                - Otherwise use CONTINUE.
                """.formatted(roleGoal, ctx.maxRounds());
    }

    private String buildTurnUserPrompt(NegotiationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Round ").append(ctx.roundNumber()).append(" of ").append(ctx.maxRounds()).append("\n");
        sb.append("Your organisation: ").append(ctx.ownOrgName()).append("\n");
        sb.append("Counterpart: ").append(ctx.counterpartOrgName()).append("\n");
        sb.append("Item: ").append(ctx.itemName()).append(" (").append(ctx.categoryName()).append(")\n");
        sb.append("Description: ").append(nullToEmpty(ctx.itemDescription())).append("\n");
        sb.append("Your target price/unit: ₹").append(ctx.ownTargetPrice()).append("\n");
        sb.append("Your target quantity: ").append(ctx.ownTargetQuantity()).append(" ").append(ctx.unit()).append("\n");
        sb.append("Distance: ").append(ctx.distanceKm()).append(" km\n");

        if (ctx.counterpartLastMessage() != null) {
            sb.append("Counterpart last message: ").append(ctx.counterpartLastMessage()).append("\n");
            sb.append("Counterpart last price: ₹").append(ctx.counterpartLastPrice()).append("\n");
            sb.append("Counterpart last quantity: ").append(ctx.counterpartLastQuantity()).append("\n");
        }

        if (!ctx.turnHistory().isEmpty()) {
            sb.append("\nTranscript so far:\n");
            for (TurnHistoryEntry entry : ctx.turnHistory()) {
                sb.append("- ").append(entry.roleLabel()).append(": ").append(entry.message());
                if (entry.price() != null) {
                    sb.append(" [price=").append(entry.price()).append(", qty=").append(entry.quantity()).append("]");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String buildReportUserPrompt(ReportContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Write the report for organisation: ").append(ctx.forOrgName()).append("\n");
        sb.append("Counterpart organisation: ").append(ctx.counterpartOrgName()).append("\n");
        sb.append("Item: ").append(ctx.itemName()).append("\n");
        sb.append("Outcome: ").append(ctx.outcome()).append("\n");
        if (ctx.agreedPrice() != null) {
            sb.append("Agreed price/unit: ₹").append(ctx.agreedPrice()).append("\n");
            sb.append("Agreed quantity: ").append(ctx.agreedQuantity()).append(" ").append(ctx.unit()).append("\n");
        }
        if (ctx.failureReason() != null) {
            sb.append("Failure/no-deal reason: ").append(ctx.failureReason()).append("\n");
        }
        sb.append("\nTranscript:\n");
        for (TurnHistoryEntry entry : ctx.turnHistory()) {
            sb.append("- ").append(entry.roleLabel()).append(": ").append(entry.message()).append("\n");
        }
        return sb.toString();
    }

    private String callGroq(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured. Set GROQ_API_KEY environment variable.");
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.4);
            body.put("max_tokens", maxOutputTokens);
            if (model.startsWith("openai/gpt-oss")) {
                body.put("reasoning_effort", "low");
            }
            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(GROQ_CHAT_URL)
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 400
                    && response.body().contains("json_validate_failed")
                    && body.containsKey("response_format")) {
                body.remove("response_format");
                requestBody = objectMapper.writeValueAsString(body);
                request = HttpRequest.newBuilder()
                        .uri(GROQ_CHAT_URL)
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Groq API error (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Groq API call interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Groq API call failed: " + e.getMessage(), e);
        }
    }

    private ReportResult parseReportJson(String raw) throws JsonProcessingException {
        String cleaned = stripMarkdownFences(raw);
        JsonNode node = objectMapper.readTree(cleaned);
        if (node == null || !node.isObject() || !node.hasNonNull("summary") || !node.hasNonNull("recommendation")) {
            throw new JsonProcessingException("Report response did not contain summary and recommendation") {};
        }
        String summary = node.path("summary").asText("Negotiation completed.");
        String recommendation = node.path("recommendation").asText("Review the transcript and decide next steps.");
        String dealTerms = node.has("dealTerms") && !node.path("dealTerms").isNull()
                ? node.path("dealTerms").asText(null)
                : null;
        return new ReportResult(summary, recommendation, dealTerms);
    }

    private AgentTurnResult parseTurnJson(String raw) throws JsonProcessingException {
        String cleaned = stripMarkdownFences(raw);
        JsonNode node = objectMapper.readTree(cleaned);
        if (node == null || !node.isObject() || !node.hasNonNull("message") || !node.hasNonNull("decision")) {
            throw new JsonProcessingException("Turn response did not contain message and decision") {};
        }

        String message = node.path("message").asText();
        BigDecimal price = node.hasNonNull("proposedPricePerUnit")
                ? BigDecimal.valueOf(node.path("proposedPricePerUnit").asDouble())
                : null;
        BigDecimal quantity = node.hasNonNull("proposedQuantity")
                ? BigDecimal.valueOf(node.path("proposedQuantity").asDouble())
                : null;
        TurnDecision decision = parseDecision(node.path("decision").asText("CONTINUE"));

        return new AgentTurnResult(message, price, quantity, decision);
    }

    private AgentTurnResult fallbackTurn(NegotiationContext ctx, String reason) {
        return new AgentTurnResult(
            (reason == null || reason.isBlank() ? "Model did not return a usable response." : reason)
                + " Proposed price: ₹" + ctx.ownTargetPrice() + " per " + ctx.unit()
                + ", quantity: " + ctx.ownTargetQuantity() + ".",
                ctx.ownTargetPrice(),
                ctx.ownTargetQuantity(),
                TurnDecision.CONTINUE
        );
    }

    private ReportResult fallbackReport(ReportContext ctx) {
        StringBuilder transcript = new StringBuilder();
        for (TurnHistoryEntry entry : ctx.turnHistory()) {
            transcript.append(entry.roleLabel())
                .append(" proposed price ₹").append(entry.price())
                .append(" and quantity ").append(entry.quantity())
                .append("; message: ").append(entry.message()).append("\n");
        }
        String summary = "Negotiation with " + ctx.counterpartOrgName() + " for " + ctx.itemName()
            + " ended with status " + ctx.outcome() + " after " + ctx.turnHistory().size()
            + " recorded turns.\n\nTurn details:\n" + transcript;
        String recommendation = ctx.outcome() == NegotiationStatus.DEAL_REACHED
            ? "Verify the agreed price, quantity, unit, delivery terms, and supporting documents offline before proceeding."
            : "No binding terms were agreed. Review the final proposals and the stated failure reason, then adjust price, quantity, or delivery expectations before reconnecting."
                + (ctx.failureReason() == null ? "" : " Reason: " + ctx.failureReason());
        String dealTerms = ctx.agreedPrice() != null
            ? "Agreed price: ₹" + ctx.agreedPrice() + " per " + ctx.unit() + "\nAgreed quantity: " + ctx.agreedQuantity()
            : "No agreed price or quantity. See the turn details above for the final proposals.";
        return new ReportResult(summary, recommendation, dealTerms);
    }

        private String readableModelText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Model returned an empty response.";
        }
        return stripMarkdownFences(raw);
        }

    private TurnDecision parseDecision(String value) {
        try {
            return TurnDecision.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurnDecision.CONTINUE;
        }
    }

    private String stripMarkdownFences(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
