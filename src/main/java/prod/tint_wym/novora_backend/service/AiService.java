package prod.tint_wym.novora_backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import prod.tint_wym.novora_backend.dto.AiDtos;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String DISCLAIMER =
            "AI suggested - review before acting. No automated people decisions.";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;
    private final String model;

    public AiService(
            @Value("${app.ai.enabled:true}") boolean enabled,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.base-url:}") String baseUrl,
            @Value("${app.ai.model:gemini-2.0-flash}") String model
    ) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        String root = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        this.restClient = RestClient.builder().baseUrl(root).build();
    }

    public AiDtos.DashboardInsightResponse dashboardInsights(AiDtos.DashboardInsightRequest request) {
        if (canCallModel()) {
            try {
                String prompt = buildDashboardPrompt(request);
                String raw = generateContent(
                        "You are an HR operations co-pilot for Novora HRMS. "
                                + "Return exactly 3 short actionable insights for an HR manager. "
                                + "Each insight must be one sentence. No numbering. No markdown.",
                        prompt);
                List<String> lines = parseInsightLines(raw);
                if (lines.size() >= 2) {
                    return new AiDtos.DashboardInsightResponse(
                            lines.subList(0, Math.min(3, lines.size())), "gemini", DISCLAIMER);
                }
            } catch (Exception ex) {
                log.warn("Gemini dashboard insights fell back to heuristics: {}", ex.getMessage());
            }
        }
        return new AiDtos.DashboardInsightResponse(heuristicDashboardInsights(request), "heuristic", DISCLAIMER);
    }

    public AiDtos.HelpdeskDraftResponse helpdeskDraft(AiDtos.HelpdeskDraftRequest request) {
        if (canCallModel()) {
            try {
                String prompt = buildHelpdeskPrompt(request);
                String draft = generateContent(
                        "You are an HR helpdesk agent for Novora. Draft a polite, concise reply "
                                + "(3-6 sentences) the HR admin can edit and send. Do not invent "
                                + "company policies. Ask for missing details when needed. Plain text only.",
                        prompt);
                if (draft != null && !draft.isBlank()) {
                    return new AiDtos.HelpdeskDraftResponse(draft.trim(), "gemini", DISCLAIMER);
                }
            } catch (Exception ex) {
                log.warn("Gemini helpdesk draft fell back to heuristics: {}", ex.getMessage());
            }
        }
        return new AiDtos.HelpdeskDraftResponse(heuristicHelpdeskDraft(request), "heuristic", DISCLAIMER);
    }

    private boolean canCallModel() {
        return enabled && !apiKey.isBlank();
    }

    /**
     * Calls Gemini {@code models/{model}:generateContent} with system instruction + user prompt.
     * Auth: {@code x-goog-api-key} header (Google AI Studio / Gemini API key).
     */
    private String generateContent(String system, String user) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", system))
        );
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", user))
        );
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", 1024);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemInstruction);
        body.put("contents", List.of(userContent));
        body.put("generationConfig", generationConfig);

        String path = "/models/" + model + ":generateContent";
        String json = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", apiKey)
                .body(body)
                .retrieve()
                .body(String.class);

        return extractGeminiText(json);
    }

    private static String extractGeminiText(String json) {
        try {
            Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json == null ? "{}" : json);
            Object candidatesObj = root.get("candidates");
            if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
                Object feedback = root.get("promptFeedback");
                if (feedback instanceof Map<?, ?> fb) {
                    Object block = fb.get("blockReason");
                    if (block != null && !String.valueOf(block).isBlank()) {
                        throw new IllegalStateException("Gemini blocked prompt: " + block);
                    }
                }
                return "";
            }
            Object first = candidates.get(0);
            if (!(first instanceof Map<?, ?> candidate)) {
                return "";
            }
            Object contentObj = candidate.get("content");
            if (!(contentObj instanceof Map<?, ?> content)) {
                return "";
            }
            Object partsObj = content.get("parts");
            if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            for (Object partObj : parts) {
                if (!(partObj instanceof Map<?, ?> part)) {
                    continue;
                }
                Object piece = part.get("text");
                if (piece == null) {
                    continue;
                }
                String value = String.valueOf(piece).trim();
                if (value.isBlank()) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(value);
            }
            return text.toString().trim();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Gemini response", ex);
        }
    }

    private static String buildDashboardPrompt(AiDtos.DashboardInsightRequest request) {
        StringBuilder sb = new StringBuilder("Current HR snapshot:\n");
        if (request.kpis() != null) {
            for (AiDtos.KpiHint k : request.kpis()) {
                if (k == null) continue;
                sb.append("- ").append(nullToDash(k.label())).append(": ")
                        .append(nullToDash(k.value()))
                        .append(" (").append(nullToDash(k.delta())).append(")\n");
            }
        }
        if (request.attendanceRate() != null) {
            sb.append("- Attendance rate: ").append(request.attendanceRate()).append("%\n");
        }
        if (request.openRoles() != null) {
            sb.append("- Open roles: ").append(request.openRoles()).append('\n');
        }
        if (request.pendingLeave() != null) {
            sb.append("- Pending leave: ").append(request.pendingLeave()).append('\n');
        }
        if (request.upcomingInterviews() != null) {
            sb.append("- Upcoming interviews: ").append(request.upcomingInterviews()).append('\n');
        }
        if (request.onboardingIncomplete() != null) {
            sb.append("- Incomplete onboarding: ").append(request.onboardingIncomplete()).append('\n');
        }
        return sb.toString();
    }

    private static String buildHelpdeskPrompt(AiDtos.HelpdeskDraftRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ticket subject: ").append(nullToDash(request.subject())).append('\n');
        sb.append("Category: ").append(nullToDash(request.category())).append('\n');
        sb.append("Priority: ").append(nullToDash(request.priority())).append('\n');
        sb.append("Requester: ").append(nullToDash(request.requesterName())).append('\n');
        sb.append("Description:\n").append(nullToDash(request.description())).append('\n');
        if (request.recentReplies() != null && !request.recentReplies().isEmpty()) {
            sb.append("Recent thread:\n");
            for (String r : request.recentReplies()) {
                if (r == null || r.isBlank()) continue;
                sb.append("- ").append(r.trim()).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<String> parseInsightLines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String line : raw.split("\\R")) {
            String cleaned = line.trim()
                    .replaceFirst("^[-*•]\\s*", "")
                    .replaceFirst("^\\d+[.)]\\s*", "");
            if (cleaned.length() > 12) {
                out.add(cleaned);
            }
        }
        return out;
    }

    private static List<String> heuristicDashboardInsights(AiDtos.DashboardInsightRequest request) {
        List<String> insights = new ArrayList<>();
        Double attendance = request.attendanceRate();
        Integer openRoles = request.openRoles();
        Integer pendingLeave = request.pendingLeave();
        Integer interviews = request.upcomingInterviews();
        Integer onboarding = request.onboardingIncomplete();

        if (attendance != null && attendance < 90) {
            insights.add("Attendance is at "
                    + String.format(Locale.US, "%.0f", attendance)
                    + "% - review late/absent patterns in Attendance before week-end.");
        } else if (attendance != null) {
            insights.add("Attendance holds at "
                    + String.format(Locale.US, "%.0f", attendance)
                    + "% present - keep an eye on late punches this week.");
        }

        if (openRoles != null && openRoles > 0) {
            insights.add(openRoles + " open role"
                    + (openRoles == 1 ? "" : "s")
                    + " in Recruitment - prioritise screening to keep time-to-hire down.");
        }

        if (pendingLeave != null && pendingLeave > 0) {
            insights.add(pendingLeave + " leave request"
                    + (pendingLeave == 1 ? "" : "s")
                    + " waiting - clear the Leave queue to unblock payroll planning.");
        }

        if (interviews != null && interviews > 0) {
            insights.add(interviews + " upcoming interview"
                    + (interviews == 1 ? "" : "s")
                    + " - confirm panels and scorecards in Recruitment.");
        }

        if (onboarding != null && onboarding > 0) {
            insights.add(onboarding + " new hire"
                    + (onboarding == 1 ? "" : "s")
                    + " still mid-onboarding - nudge incomplete checklist owners.");
        }

        if (insights.isEmpty()) {
            insights.add("Workforce metrics look steady - scan Hiring Funnel and Needs Attention for the next action.");
            insights.add("Use Punch In/Out on the dashboard to keep today's attendance current.");
            insights.add("Open Reports if you need a deeper export for leadership.");
        }

        while (insights.size() < 3) {
            insights.add("Review Engagement and Learning modules for retention signals this month.");
            if (insights.size() >= 3) break;
            insights.add("Keep Open Roles and New Hires aligned so headcount planning stays accurate.");
        }
        return insights.subList(0, Math.min(3, insights.size()));
    }

    private static String heuristicHelpdeskDraft(AiDtos.HelpdeskDraftRequest request) {
        String name = request.requesterName() == null || request.requesterName().isBlank()
                ? "there"
                : request.requesterName().trim().split("\\s+")[0];
        String subject = nullToDash(request.subject());
        String category = nullToDash(request.category()).toLowerCase(Locale.US);
        StringBuilder draft = new StringBuilder();
        draft.append("Hi ").append(name).append(",\n\n");
        draft.append("Thanks for reaching out about \"").append(subject).append("\". ");
        draft.append("I've reviewed your ticket");
        if (!category.isBlank() && !"-".equals(category)) {
            draft.append(" under ").append(category);
        }
        draft.append(" and I'm looking into this now.\n\n");
        draft.append("To help us resolve this quickly, please confirm any relevant dates, "
                + "employee ID (if applicable), and attach supporting documents if you haven't already.\n\n");
        draft.append("I'll update you as soon as I have next steps.\n\n");
        draft.append("Best regards,\nHR Support");
        return draft.toString();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
