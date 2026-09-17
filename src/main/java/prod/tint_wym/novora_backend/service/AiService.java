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

    public AiDtos.RecruitmentJdDraftResponse recruitmentJdDraft(AiDtos.RecruitmentJdDraftRequest request) {
        if (canCallModel()) {
            try {
                String draft = generateContent(
                        "You are a recruitment specialist for Novora HRMS. Draft a clear job description "
                                + "in plain text with these sections: Summary, Key responsibilities (bullet lines "
                                + "starting with - ), Requirements, Nice-to-have. Keep it concise (180-320 words). "
                                + "Do not invent company policies, benefits, or salary figures not provided. "
                                + "No markdown headings with #.",
                        buildJdPrompt(request),
                        1600);
                if (draft != null && !draft.isBlank()) {
                    return new AiDtos.RecruitmentJdDraftResponse(draft.trim(), "gemini", DISCLAIMER);
                }
            } catch (Exception ex) {
                log.warn("Gemini JD draft fell back to heuristics: {}", ex.getMessage());
            }
        }
        return new AiDtos.RecruitmentJdDraftResponse(heuristicJdDraft(request), "heuristic", DISCLAIMER);
    }

    public AiDtos.CandidateSummaryResponse candidateSummary(AiDtos.CandidateSummaryRequest request) {
        if (canCallModel()) {
            try {
                String raw = generateContent(
                        "You are a recruiting co-pilot for Novora HRMS. Return plain text only with exactly "
                                + "these labeled sections:\n"
                                + "SUMMARY: one short paragraph\n"
                                + "STRENGTHS:\n- item\n- item\n- item\n"
                                + "RISKS:\n- item\n- item\n"
                                + "QUESTIONS:\n- interview question\n- interview question\n- interview question\n"
                                + "Do not invent employment history. If notes are thin, say what is missing. "
                                + "This is a screening aid only - not a hiring decision.",
                        buildCandidatePrompt(request),
                        1200);
                AiDtos.CandidateSummaryResponse parsed = parseCandidateSummary(raw);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("Gemini candidate summary fell back to heuristics: {}", ex.getMessage());
            }
        }
        return heuristicCandidateSummary(request);
    }

    public AiDtos.PerformanceReviewDraftResponse performanceReviewDraft(
            AiDtos.PerformanceReviewDraftRequest request) {
        if (canCallModel()) {
            try {
                String draft = generateContent(
                        "You are a performance manager coach for Novora HRMS. Draft a balanced appraiser note "
                                + "(4-7 sentences) covering strengths, one improvement area, and a next-period focus. "
                                + "Be professional and fair. Do not invent scores not provided. Plain text only. "
                                + "This is a draft for the manager to edit - not a final rating decision.",
                        buildPerformancePrompt(request),
                        900);
                if (draft != null && !draft.isBlank()) {
                    return new AiDtos.PerformanceReviewDraftResponse(draft.trim(), "gemini", DISCLAIMER);
                }
            } catch (Exception ex) {
                log.warn("Gemini performance draft fell back to heuristics: {}", ex.getMessage());
            }
        }
        return new AiDtos.PerformanceReviewDraftResponse(heuristicPerformanceDraft(request), "heuristic", DISCLAIMER);
    }

    public AiDtos.CourseRecommendationResponse courseRecommendations(
            AiDtos.CourseRecommendationRequest request) {
        if (canCallModel()) {
            try {
                String raw = generateContent(
                        "You are an L&D advisor for Novora HRMS. Recommend exactly 3 courses from the catalog "
                                + "when possible. Return plain text:\n"
                                + "RECOMMENDATIONS:\n- course title - why\n- course title - why\n- course title - why\n"
                                + "RATIONALE: one short paragraph\n"
                                + "Prefer titles from the provided catalog. If the catalog is empty, suggest generic topics.",
                        buildCoursePrompt(request),
                        900);
                AiDtos.CourseRecommendationResponse parsed = parseCourseRecommendations(raw);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("Gemini course recommendations fell back to heuristics: {}", ex.getMessage());
            }
        }
        return heuristicCourseRecommendations(request);
    }

    public AiDtos.EngagementThemeResponse engagementThemes(AiDtos.EngagementThemeRequest request) {
        if (canCallModel()) {
            try {
                String raw = generateContent(
                        "You are an employee-engagement analyst for Novora HRMS. Aggregate themes only - "
                                + "do not identify individuals. Return plain text:\n"
                                + "SUMMARY: one short paragraph\n"
                                + "THEMES:\n- theme\n- theme\n- theme\n"
                                + "ACTIONS:\n- suggested HR action\n- suggested HR action\n"
                                + "Stay constructive and anonymised.",
                        buildEngagementPrompt(request),
                        1000);
                AiDtos.EngagementThemeResponse parsed = parseEngagementThemes(raw);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("Gemini engagement themes fell back to heuristics: {}", ex.getMessage());
            }
        }
        return heuristicEngagementThemes(request);
    }

    public AiDtos.DisciplinaryLetterResponse disciplinaryLetter(AiDtos.DisciplinaryLetterRequest request) {
        if (canCallModel()) {
            try {
                String raw = generateContent(
                        "You are an HR employee-relations advisor for Novora HRMS. Draft a formal but fair "
                                + "warning letter and a short chronology. Return plain text:\n"
                                + "LETTER:\n(full letter body)\n"
                                + "CHRONOLOGY:\n- dated bullet\n- dated bullet\n"
                                + "Do not invent facts not provided. Include a clear statement that the employee "
                                + "may respond and that this is not automatic termination. Manager must review before issue.",
                        buildDisciplinaryPrompt(request),
                        1600);
                AiDtos.DisciplinaryLetterResponse parsed = parseDisciplinaryLetter(raw);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("Gemini disciplinary letter fell back to heuristics: {}", ex.getMessage());
            }
        }
        return heuristicDisciplinaryLetter(request);
    }

    public AiDtos.PayrollAnomalyResponse payrollAnomalies(AiDtos.PayrollAnomalyRequest request) {
        if (canCallModel()) {
            try {
                String raw = generateContent(
                        "You are a payroll QA assistant for Novora HRMS. Explain possible anomalies only — "
                                + "never change amounts. Return plain text:\n"
                                + "SUMMARY:\n(1-2 sentences)\n"
                                + "FINDINGS:\n- bullet\n- bullet\n"
                                + "Do not invent employees or figures not provided. Remind that finance must verify before pay.",
                        buildPayrollPrompt(request),
                        900);
                AiDtos.PayrollAnomalyResponse parsed = parsePayrollAnomalies(raw);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("Gemini payroll anomalies fell back to heuristics: {}", ex.getMessage());
            }
        }
        return heuristicPayrollAnomalies(request);
    }

    public AiDtos.BenefitsTipResponse benefitsTip(AiDtos.BenefitsTipRequest request) {
        if (canCallModel()) {
            try {
                String raw = generateContent(
                        "You are a benefits enrollment coach for Novora HRMS. Suggest helpful tips only — "
                                + "never enroll anyone. Return plain text:\n"
                                + "TIP:\n(short paragraph)\n"
                                + "SUGGESTIONS:\n- bullet\n- bullet\n"
                                + "Stay factual from provided plan names; employee decides.",
                        buildBenefitsPrompt(request),
                        700);
                AiDtos.BenefitsTipResponse parsed = parseBenefitsTip(raw);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("Gemini benefits tip fell back to heuristics: {}", ex.getMessage());
            }
        }
        return heuristicBenefitsTip(request);
    }

    public AiDtos.AssetsInsightResponse assetsInsights(AiDtos.AssetsInsightRequest request) {
        if (canCallModel()) {
            try {
                String raw = generateContent(
                        "You are an IT asset operations assistant for Novora HRMS. Suggest inventory insights only — "
                                + "never reassign assets. Return plain text:\n"
                                + "SUMMARY:\n(1-2 sentences)\n"
                                + "INSIGHTS:\n- bullet\n- bullet\n"
                                + "Do not invent serial numbers or custodians not provided.",
                        buildAssetsPrompt(request),
                        700);
                AiDtos.AssetsInsightResponse parsed = parseAssetsInsights(raw);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("Gemini assets insights fell back to heuristics: {}", ex.getMessage());
            }
        }
        return heuristicAssetsInsights(request);
    }

    private boolean canCallModel() {
        return enabled && !apiKey.isBlank();
    }

    /**
     * Calls Gemini {@code models/{model}:generateContent} with system instruction + user prompt.
     * Auth: {@code x-goog-api-key} header (Google AI Studio / Gemini API key).
     * Text drafts use generateContent (not Live/WebSocket — Live is for realtime voice/video).
     */
    private String generateContent(String system, String user) {
        return generateContent(system, user, 1024);
    }

    private String generateContent(String system, String user, int maxOutputTokens) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", system))
        );
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", user))
        );
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", Math.max(256, maxOutputTokens));

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

    private static String buildJdPrompt(AiDtos.RecruitmentJdDraftRequest request) {
        StringBuilder sb = new StringBuilder("Draft a job description from this requisition:\n");
        sb.append("- Title: ").append(nullToDash(request.title())).append('\n');
        sb.append("- Department: ").append(nullToDash(request.department())).append('\n');
        sb.append("- Employment type: ").append(nullToDash(request.employmentType())).append('\n');
        sb.append("- Location: ").append(nullToDash(request.location())).append('\n');
        sb.append("- Experience: ").append(nullToDash(request.experience())).append('\n');
        sb.append("- Education: ").append(nullToDash(request.education())).append('\n');
        sb.append("- Skills: ").append(nullToDash(request.skills())).append('\n');
        if ((request.salaryMin() != null && !request.salaryMin().isBlank())
                || (request.salaryMax() != null && !request.salaryMax().isBlank())) {
            sb.append("- Salary range: ").append(nullToDash(request.salaryMin()))
                    .append(" - ").append(nullToDash(request.salaryMax())).append('\n');
        }
        sb.append("- Existing responsibilities notes:\n")
                .append(nullToDash(request.existingResponsibilities())).append('\n');
        sb.append("- Nice-to-have notes:\n").append(nullToDash(request.niceToHave())).append('\n');
        return sb.toString();
    }

    private static String buildCandidatePrompt(AiDtos.CandidateSummaryRequest request) {
        StringBuilder sb = new StringBuilder("Screen this candidate for hiring managers:\n");
        sb.append("- Name: ").append(nullToDash(request.fullName())).append('\n');
        sb.append("- Role applied: ").append(nullToDash(request.jobTitle())).append('\n');
        sb.append("- Stage: ").append(nullToDash(request.stage())).append('\n');
        sb.append("- Source: ").append(nullToDash(request.source())).append('\n');
        sb.append("- Rating: ").append(nullToDash(request.rating())).append('\n');
        sb.append("- Email: ").append(nullToDash(request.email())).append('\n');
        sb.append("- Phone: ").append(nullToDash(request.phone())).append('\n');
        sb.append("- Notes / application text:\n").append(nullToDash(request.notes())).append('\n');
        return sb.toString();
    }

    private static String heuristicJdDraft(AiDtos.RecruitmentJdDraftRequest request) {
        String title = nullToDash(request.title());
        String dept = nullToDash(request.department());
        String skills = nullToDash(request.skills());
        String experience = nullToDash(request.experience());
        String education = nullToDash(request.education());
        String existing = request.existingResponsibilities() == null || request.existingResponsibilities().isBlank()
                ? null
                : request.existingResponsibilities().trim();
        String nice = request.niceToHave() == null || request.niceToHave().isBlank()
                ? "Related industry experience and strong communication skills."
                : request.niceToHave().trim();

        StringBuilder sb = new StringBuilder();
        sb.append("Summary\n");
        sb.append("We are hiring a ").append(title).append(" to join the ").append(dept)
                .append(" team. This ").append(nullToDash(request.employmentType()))
                .append(" role supports day-to-day delivery and continuous improvement.\n\n");
        sb.append("Key responsibilities\n");
        if (existing != null) {
            for (String line : existing.split("\\R")) {
                String cleaned = line.trim().replaceFirst("^[-*•]\\s*", "");
                if (!cleaned.isBlank()) {
                    sb.append("- ").append(cleaned).append('\n');
                }
            }
        } else {
            sb.append("- Own core deliverables for the ").append(title).append(" function\n");
            sb.append("- Partner with stakeholders across ").append(dept).append(" and related teams\n");
            sb.append("- Maintain accurate records and escalate risks early\n");
            sb.append("- Contribute to process improvement and knowledge sharing\n");
        }
        sb.append("\nRequirements\n");
        sb.append("- ").append(education).append('\n');
        sb.append("- ").append(experience).append('\n');
        if (!"-".equals(skills)) {
            sb.append("- Working knowledge of: ").append(skills).append('\n');
        }
        sb.append("\nNice-to-have\n");
        for (String line : nice.split("\\R")) {
            String cleaned = line.trim().replaceFirst("^[-*•]\\s*", "");
            if (!cleaned.isBlank()) {
                sb.append("- ").append(cleaned).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private AiDtos.CandidateSummaryResponse parseCandidateSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String summary = "";
        List<String> strengths = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        String section = "";
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            String upper = trimmed.toUpperCase(Locale.US);
            if (upper.startsWith("SUMMARY:")) {
                section = "summary";
                summary = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                continue;
            }
            if (upper.equals("SUMMARY")) {
                section = "summary";
                continue;
            }
            if (upper.startsWith("STRENGTHS")) {
                section = "strengths";
                continue;
            }
            if (upper.startsWith("RISKS") || upper.startsWith("GAPS")) {
                section = "risks";
                continue;
            }
            if (upper.startsWith("QUESTIONS") || upper.startsWith("INTERVIEW")) {
                section = "questions";
                continue;
            }
            String cleaned = trimmed.replaceFirst("^[-*•]\\s*", "").replaceFirst("^\\d+[.)]\\s*", "");
            switch (section) {
                case "summary" -> {
                    if (summary.isBlank()) summary = cleaned;
                    else summary = summary + " " + cleaned;
                }
                case "strengths" -> {
                    if (cleaned.length() > 3) strengths.add(cleaned);
                }
                case "risks" -> {
                    if (cleaned.length() > 3) risks.add(cleaned);
                }
                case "questions" -> {
                    if (cleaned.length() > 3) questions.add(cleaned);
                }
                default -> {
                    // ignore preamble
                }
            }
        }
        if (summary.isBlank() || strengths.size() + questions.size() < 2) {
            return null;
        }
        return new AiDtos.CandidateSummaryResponse(
                summary,
                strengths.isEmpty() ? List.of("Review notes for transferable skills") : strengths.subList(0, Math.min(5, strengths.size())),
                risks.isEmpty() ? List.of("Confirm experience depth in interview") : risks.subList(0, Math.min(5, risks.size())),
                questions.isEmpty()
                        ? List.of("Walk us through a relevant recent project.")
                        : questions.subList(0, Math.min(5, questions.size())),
                "gemini",
                DISCLAIMER);
    }

    private static AiDtos.CandidateSummaryResponse heuristicCandidateSummary(AiDtos.CandidateSummaryRequest request) {
        String name = nullToDash(request.fullName());
        String role = nullToDash(request.jobTitle());
        String notes = request.notes() == null || request.notes().isBlank()
                ? "Limited application notes on file."
                : request.notes().trim();
        String summary = name + " is at stage " + nullToDash(request.stage())
                + " for " + role + " (source: " + nullToDash(request.source()) + "). "
                + "Review the notes below before advancing. " + notes;
        if (summary.length() > 480) {
            summary = summary.substring(0, 477) + "...";
        }
        List<String> strengths = new ArrayList<>();
        strengths.add("Active pipeline candidate for " + role);
        if (request.rating() != null && !request.rating().isBlank() && !"-".equals(request.rating())) {
            strengths.add("Current rating on file: " + request.rating());
        }
        strengths.add("Contact available for follow-up screening");

        List<String> risks = new ArrayList<>();
        if (request.notes() == null || request.notes().isBlank()) {
            risks.add("Application notes are empty - request resume details before panel");
        } else {
            risks.add("Validate claimed experience against the role requirements");
        }
        risks.add("Do not treat this summary as an automated hiring decision");

        List<String> questions = List.of(
                "Walk us through a recent project most similar to " + role + ".",
                "What would you prioritise in the first 90 days in this role?",
                "Describe a stakeholder conflict you resolved and the outcome."
        );
        return new AiDtos.CandidateSummaryResponse(summary, strengths, risks, questions, "heuristic", DISCLAIMER);
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

    private static String buildPerformancePrompt(AiDtos.PerformanceReviewDraftRequest request) {
        StringBuilder sb = new StringBuilder("Draft an appraiser note for:\n");
        sb.append("- Employee: ").append(nullToDash(request.employeeName())).append('\n');
        sb.append("- Review type: ").append(nullToDash(request.reviewType())).append('\n');
        sb.append("- Period: ").append(nullToDash(request.reviewPeriod())).append('\n');
        sb.append("- Date: ").append(nullToDash(request.reviewDate())).append('\n');
        sb.append("- Scores: codeQuality=").append(nullToDash(request.codeQuality()))
                .append(", problemSolving=").append(nullToDash(request.problemSolving()))
                .append(", systemDesign=").append(nullToDash(request.systemDesign()))
                .append(", sprints=").append(nullToDash(request.sprintsCompleted()))
                .append(", bugsSLA=").append(nullToDash(request.bugsSla()))
                .append(", attendance=").append(nullToDash(request.attendance())).append('\n');
        sb.append("- Existing note:\n").append(nullToDash(request.existingNote())).append('\n');
        return sb.toString();
    }

    private static String heuristicPerformanceDraft(AiDtos.PerformanceReviewDraftRequest request) {
        return "During " + nullToDash(request.reviewPeriod()) + ", "
                + nullToDash(request.employeeName())
                + " demonstrated solid delivery against the recorded scores for this "
                + nullToDash(request.reviewType())
                + ". Strengths appear in consistent execution and collaboration. "
                + "One focus area for the next period is closing gaps highlighted by the lower score dimensions "
                + "and agreeing measurable targets with the manager. "
                + "Please review and personalise this note before sharing with the employee.";
    }

    private static String buildCoursePrompt(AiDtos.CourseRecommendationRequest request) {
        StringBuilder sb = new StringBuilder("Recommend learning based on:\n");
        sb.append("- Department: ").append(nullToDash(request.department())).append('\n');
        sb.append("- Role / focus: ").append(nullToDash(request.roleOrFocus())).append('\n');
        sb.append("- Skills gap: ").append(nullToDash(request.skillsGap())).append('\n');
        sb.append("- Catalog titles:\n");
        if (request.catalogTitles() != null) {
            for (String t : request.catalogTitles()) {
                if (t != null && !t.isBlank()) sb.append("  - ").append(t.trim()).append('\n');
            }
        }
        sb.append("- Categories:\n");
        if (request.categories() != null) {
            for (String c : request.categories()) {
                if (c != null && !c.isBlank()) sb.append("  - ").append(c.trim()).append('\n');
            }
        }
        return sb.toString();
    }

    private AiDtos.CourseRecommendationResponse parseCourseRecommendations(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<String> recs = new ArrayList<>();
        String rationale = "";
        String section = "";
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            String upper = trimmed.toUpperCase(Locale.US);
            if (upper.startsWith("RECOMMENDATIONS")) {
                section = "recs";
                continue;
            }
            if (upper.startsWith("RATIONALE")) {
                section = "rationale";
                if (trimmed.contains(":")) {
                    rationale = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                }
                continue;
            }
            String cleaned = trimmed.replaceFirst("^[-*•]\\s*", "").replaceFirst("^\\d+[.)]\\s*", "");
            if ("recs".equals(section) && cleaned.length() > 4) {
                recs.add(cleaned);
            } else if ("rationale".equals(section)) {
                rationale = rationale.isBlank() ? cleaned : rationale + " " + cleaned;
            }
        }
        if (recs.isEmpty()) return null;
        return new AiDtos.CourseRecommendationResponse(
                recs.subList(0, Math.min(5, recs.size())),
                rationale.isBlank() ? "Selected from the current catalog for the stated focus." : rationale,
                "gemini",
                DISCLAIMER);
    }

    private static AiDtos.CourseRecommendationResponse heuristicCourseRecommendations(
            AiDtos.CourseRecommendationRequest request) {
        List<String> catalog = request.catalogTitles() == null ? List.of() : request.catalogTitles().stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .toList();
        List<String> recs = new ArrayList<>();
        for (String title : catalog) {
            if (recs.size() >= 3) break;
            recs.add(title + " - matches current catalog availability");
        }
        while (recs.size() < 3) {
            recs.add("Communication & stakeholder management - foundational soft skill for most roles");
            if (recs.size() >= 3) break;
            recs.add("Workplace compliance essentials - reduces operational risk");
            if (recs.size() >= 3) break;
            recs.add("Time management & prioritisation - supports delivery consistency");
        }
        String focus = nullToDash(request.roleOrFocus());
        String rationale = "Heuristic picks for " + focus
                + " in " + nullToDash(request.department())
                + ". Prefer Gemini recommendations when a key is configured.";
        return new AiDtos.CourseRecommendationResponse(recs.subList(0, 3), rationale, "heuristic", DISCLAIMER);
    }

    private static String buildEngagementPrompt(AiDtos.EngagementThemeRequest request) {
        StringBuilder sb = new StringBuilder("Aggregate themes from anonymous feedback:\n");
        if (request.comments() != null) {
            int i = 1;
            for (AiDtos.EngagementComment c : request.comments()) {
                if (c == null || c.text() == null || c.text().isBlank()) continue;
                sb.append(i++).append(". [").append(nullToDash(c.category()))
                        .append(" / ").append(nullToDash(c.vibe())).append("] ")
                        .append(c.text().trim()).append('\n');
                if (i > 40) break;
            }
        }
        if (sb.toString().endsWith(":\n")) {
            sb.append("(no comments provided)\n");
        }
        return sb.toString();
    }

    private AiDtos.EngagementThemeResponse parseEngagementThemes(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String summary = "";
        List<String> themes = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        String section = "";
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            String upper = trimmed.toUpperCase(Locale.US);
            if (upper.startsWith("SUMMARY")) {
                section = "summary";
                if (trimmed.contains(":")) summary = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                continue;
            }
            if (upper.startsWith("THEMES")) {
                section = "themes";
                continue;
            }
            if (upper.startsWith("ACTIONS")) {
                section = "actions";
                continue;
            }
            String cleaned = trimmed.replaceFirst("^[-*•]\\s*", "").replaceFirst("^\\d+[.)]\\s*", "");
            switch (section) {
                case "summary" -> summary = summary.isBlank() ? cleaned : summary + " " + cleaned;
                case "themes" -> {
                    if (cleaned.length() > 3) themes.add(cleaned);
                }
                case "actions" -> {
                    if (cleaned.length() > 3) actions.add(cleaned);
                }
                default -> {
                }
            }
        }
        if (summary.isBlank() && themes.isEmpty()) return null;
        return new AiDtos.EngagementThemeResponse(
                themes.isEmpty() ? List.of("General workplace feedback") : themes.subList(0, Math.min(6, themes.size())),
                summary.isBlank() ? "Aggregate themes generated from recent suggestions." : summary,
                actions.isEmpty()
                        ? List.of("Share themes with managers without naming individuals")
                        : actions.subList(0, Math.min(5, actions.size())),
                "gemini",
                DISCLAIMER);
    }

    private static AiDtos.EngagementThemeResponse heuristicEngagementThemes(
            AiDtos.EngagementThemeRequest request) {
        int count = request.comments() == null ? 0 : (int) request.comments().stream()
                .filter(c -> c != null && c.text() != null && !c.text().isBlank())
                .count();
        List<String> themes = new ArrayList<>();
        if (request.comments() != null) {
            for (AiDtos.EngagementComment c : request.comments()) {
                if (c == null || c.category() == null || c.category().isBlank()) continue;
                String theme = c.category().trim() + " feedback appears repeatedly";
                if (!themes.contains(theme) && themes.size() < 4) themes.add(theme);
            }
        }
        if (themes.isEmpty()) {
            themes.add("Workload and prioritisation");
            themes.add("Communication clarity");
            themes.add("Recognition and morale");
        }
        String summary = count == 0
                ? "No suggestion text available yet - collect more anonymous feedback before acting."
                : "Reviewed " + count + " anonymous suggestion(s). Themes below are aggregate only.";
        List<String> actions = List.of(
                "Review top themes in the next people-ops standup",
                "Convert one theme into a tracked action plan owner"
        );
        return new AiDtos.EngagementThemeResponse(themes, summary, actions, "heuristic", DISCLAIMER);
    }

    private static String buildDisciplinaryPrompt(AiDtos.DisciplinaryLetterRequest request) {
        StringBuilder sb = new StringBuilder("Draft warning materials for:\n");
        sb.append("- Employee: ").append(nullToDash(request.employeeName())).append('\n');
        sb.append("- Department: ").append(nullToDash(request.department())).append('\n');
        sb.append("- Reason: ").append(nullToDash(request.reason())).append('\n');
        sb.append("- Warning level: ").append(nullToDash(request.warningLevel())).append('\n');
        sb.append("- Incident date: ").append(nullToDash(request.incidentDate())).append('\n');
        sb.append("- Location: ").append(nullToDash(request.location())).append('\n');
        sb.append("- Issued by: ").append(nullToDash(request.issuedBy())).append('\n');
        sb.append("- Description:\n").append(nullToDash(request.description())).append('\n');
        sb.append("- Existing expectation notes:\n").append(nullToDash(request.existingExpectation())).append('\n');
        return sb.toString();
    }

    private AiDtos.DisciplinaryLetterResponse parseDisciplinaryLetter(String raw) {
        if (raw == null || raw.isBlank()) return null;
        StringBuilder letter = new StringBuilder();
        StringBuilder chronology = new StringBuilder();
        String section = "";
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase(Locale.US);
            if (upper.equals("LETTER:") || upper.equals("LETTER")) {
                section = "letter";
                continue;
            }
            if (upper.startsWith("CHRONOLOGY")) {
                section = "chronology";
                continue;
            }
            if ("letter".equals(section)) {
                if (!letter.isEmpty()) letter.append('\n');
                letter.append(line);
            } else if ("chronology".equals(section)) {
                if (!chronology.isEmpty()) chronology.append('\n');
                chronology.append(line);
            }
        }
        String letterText = letter.toString().trim();
        if (letterText.isBlank()) return null;
        String chronoText = chronology.toString().trim();
        if (chronoText.isBlank()) {
            chronoText = "- Incident recorded pending manager review";
        }
        return new AiDtos.DisciplinaryLetterResponse(letterText, chronoText, "gemini", DISCLAIMER);
    }

    private static AiDtos.DisciplinaryLetterResponse heuristicDisciplinaryLetter(
            AiDtos.DisciplinaryLetterRequest request) {
        String name = nullToDash(request.employeeName());
        String reason = nullToDash(request.reason());
        String level = nullToDash(request.warningLevel());
        String date = nullToDash(request.incidentDate());
        StringBuilder letter = new StringBuilder();
        letter.append("Dear ").append(name).append(",\n\n");
        letter.append("This letter records a ").append(level)
                .append(" regarding ").append(reason);
        if (!"-".equals(date)) {
            letter.append(" on ").append(date);
        }
        letter.append(".\n\n");
        letter.append("Incident summary:\n")
                .append(nullToDash(request.description())).append("\n\n");
        letter.append("Expected standard going forward:\n");
        if (request.existingExpectation() != null && !request.existingExpectation().isBlank()) {
            letter.append(request.existingExpectation().trim()).append("\n\n");
        } else {
            letter.append("Maintain professional conduct consistent with company policy and manager guidance.\n\n");
        }
        letter.append("You may provide a written response within a reasonable period. ")
                .append("This draft must be reviewed by HR/management before issue and does not itself ")
                .append("constitute automatic termination.\n\n");
        letter.append("Regards,\n").append(nullToDash(request.issuedBy()));
        String chronology = "- " + date + ": Incident logged (" + reason + ")\n"
                + "- Draft " + level + " prepared for review\n"
                + "- Pending manager/HR approval before issue";
        return new AiDtos.DisciplinaryLetterResponse(letter.toString(), chronology, "heuristic", DISCLAIMER);
    }

    private static String buildPayrollPrompt(AiDtos.PayrollAnomalyRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Period: ").append(nullToDash(String.valueOf(request.payMonth()))).append('/')
                .append(nullToDash(String.valueOf(request.payYear()))).append('\n');
        sb.append("Headcount: ").append(request.headcount() == null ? "-" : request.headcount()).append('\n');
        sb.append("Total net pay: ").append(nullToDash(request.totalNetPay())).append('\n');
        sb.append("Draft/Processed/Paid: ")
                .append(request.draftCount() == null ? "-" : request.draftCount()).append('/')
                .append(request.processedCount() == null ? "-" : request.processedCount()).append('/')
                .append(request.paidCount() == null ? "-" : request.paidCount()).append('\n');
        sb.append("Row count: ").append(request.rowCount() == null ? "-" : request.rowCount()).append('\n');
        if (request.sampleRows() != null && !request.sampleRows().isEmpty()) {
            sb.append("Sample rows:\n");
            for (String row : request.sampleRows()) {
                if (row != null && !row.isBlank()) {
                    sb.append("- ").append(row.trim()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private AiDtos.PayrollAnomalyResponse parsePayrollAnomalies(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String summary = "";
        List<String> findings = new ArrayList<>();
        String section = "";
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase(Locale.US);
            if (upper.startsWith("SUMMARY")) {
                section = "summary";
                continue;
            }
            if (upper.startsWith("FINDINGS")) {
                section = "findings";
                continue;
            }
            if (trimmed.isBlank()) continue;
            if ("summary".equals(section)) {
                summary = summary.isBlank() ? trimmed : summary + " " + trimmed;
            } else if ("findings".equals(section)) {
                findings.add(trimmed.replaceFirst("^[-*•]\\s*", ""));
            }
        }
        if (summary.isBlank() && findings.isEmpty()) return null;
        if (findings.isEmpty()) findings = List.of("Review draft vs processed counts before disbursement.");
        if (summary.isBlank()) summary = "Payroll QA suggestions ready for finance review.";
        return new AiDtos.PayrollAnomalyResponse(findings, summary, "gemini", DISCLAIMER);
    }

    private static AiDtos.PayrollAnomalyResponse heuristicPayrollAnomalies(AiDtos.PayrollAnomalyRequest request) {
        List<String> findings = new ArrayList<>();
        int draft = request.draftCount() == null ? 0 : request.draftCount();
        int processed = request.processedCount() == null ? 0 : request.processedCount();
        int paid = request.paidCount() == null ? 0 : request.paidCount();
        int rows = request.rowCount() == null ? 0 : request.rowCount();
        int headcount = request.headcount() == null ? 0 : request.headcount();
        if (draft > 0) {
            findings.add(draft + " draft row(s) still open — confirm before marking paid.");
        }
        if (headcount > 0 && rows > 0 && Math.abs(headcount - rows) > 0) {
            findings.add("Headcount (" + headcount + ") differs from payroll rows (" + rows + ") — reconcile missing/extra employees.");
        }
        if (processed > 0 && paid == 0) {
            findings.add("Rows are processed but none paid yet — verify bank file / disbursement step.");
        }
        if (findings.isEmpty()) {
            findings.add("No obvious status mismatch from the supplied totals — still spot-check OT and deductions.");
        }
        String summary = "Heuristic payroll QA for "
                + nullToDash(String.valueOf(request.payMonth())) + "/"
                + nullToDash(String.valueOf(request.payYear()))
                + ". AI does not change pay amounts.";
        return new AiDtos.PayrollAnomalyResponse(findings, summary, "heuristic", DISCLAIMER);
    }

    private static String buildBenefitsPrompt(AiDtos.BenefitsTipRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Employee: ").append(nullToDash(request.employeeName())).append('\n');
        sb.append("Department: ").append(nullToDash(request.department())).append('\n');
        sb.append("Available plans:\n");
        if (request.availablePlans() != null) {
            for (String p : request.availablePlans()) {
                if (p != null && !p.isBlank()) sb.append("- ").append(p.trim()).append('\n');
            }
        }
        sb.append("Already enrolled:\n");
        if (request.enrolledPlans() != null) {
            for (String p : request.enrolledPlans()) {
                if (p != null && !p.isBlank()) sb.append("- ").append(p.trim()).append('\n');
            }
        }
        return sb.toString();
    }

    private AiDtos.BenefitsTipResponse parseBenefitsTip(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String tip = "";
        List<String> suggestions = new ArrayList<>();
        String section = "";
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase(Locale.US);
            if (upper.startsWith("TIP")) {
                section = "tip";
                continue;
            }
            if (upper.startsWith("SUGGESTIONS")) {
                section = "suggestions";
                continue;
            }
            if (trimmed.isBlank()) continue;
            if ("tip".equals(section)) {
                tip = tip.isBlank() ? trimmed : tip + " " + trimmed;
            } else if ("suggestions".equals(section)) {
                suggestions.add(trimmed.replaceFirst("^[-*•]\\s*", ""));
            }
        }
        if (tip.isBlank() && suggestions.isEmpty()) return null;
        if (tip.isBlank()) tip = "Review available plans against current enrollment before confirming.";
        if (suggestions.isEmpty()) suggestions = List.of("Compare medical vs wellness coverage gaps.");
        return new AiDtos.BenefitsTipResponse(tip, suggestions, "gemini", DISCLAIMER);
    }

    private static AiDtos.BenefitsTipResponse heuristicBenefitsTip(AiDtos.BenefitsTipRequest request) {
        List<String> available = request.availablePlans() == null ? List.of() : request.availablePlans();
        List<String> enrolled = request.enrolledPlans() == null ? List.of() : request.enrolledPlans();
        List<String> suggestions = new ArrayList<>();
        for (String plan : available) {
            if (plan == null || plan.isBlank()) continue;
            boolean already = enrolled.stream().anyMatch(e -> e != null && e.equalsIgnoreCase(plan));
            if (!already) {
                suggestions.add("Consider reviewing: " + plan.trim());
            }
            if (suggestions.size() >= 3) break;
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Enrollment looks complete for listed plans — confirm dependents and coverage dates.");
        }
        String tip = "For " + nullToDash(request.employeeName())
                + ", compare open plans against current enrollments. Suggestions only — employee decides.";
        return new AiDtos.BenefitsTipResponse(tip, suggestions, "heuristic", DISCLAIMER);
    }

    private static String buildAssetsPrompt(AiDtos.AssetsInsightRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total: ").append(request.totalAssets() == null ? "-" : request.totalAssets()).append('\n');
        sb.append("Available: ").append(request.availableCount() == null ? "-" : request.availableCount()).append('\n');
        sb.append("In use: ").append(request.inUseCount() == null ? "-" : request.inUseCount()).append('\n');
        sb.append("Maintenance: ").append(request.maintenanceCount() == null ? "-" : request.maintenanceCount()).append('\n');
        if (request.flaggedItems() != null && !request.flaggedItems().isEmpty()) {
            sb.append("Flagged items:\n");
            for (String item : request.flaggedItems()) {
                if (item != null && !item.isBlank()) sb.append("- ").append(item.trim()).append('\n');
            }
        }
        return sb.toString();
    }

    private AiDtos.AssetsInsightResponse parseAssetsInsights(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String summary = "";
        List<String> insights = new ArrayList<>();
        String section = "";
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase(Locale.US);
            if (upper.startsWith("SUMMARY")) {
                section = "summary";
                continue;
            }
            if (upper.startsWith("INSIGHTS")) {
                section = "insights";
                continue;
            }
            if (trimmed.isBlank()) continue;
            if ("summary".equals(section)) {
                summary = summary.isBlank() ? trimmed : summary + " " + trimmed;
            } else if ("insights".equals(section)) {
                insights.add(trimmed.replaceFirst("^[-*•]\\s*", ""));
            }
        }
        if (summary.isBlank() && insights.isEmpty()) return null;
        if (insights.isEmpty()) insights = List.of("Prioritize maintenance queue before new assignments.");
        if (summary.isBlank()) summary = "Asset inventory insights ready for ops review.";
        return new AiDtos.AssetsInsightResponse(insights, summary, "gemini", DISCLAIMER);
    }

    private static AiDtos.AssetsInsightResponse heuristicAssetsInsights(AiDtos.AssetsInsightRequest request) {
        List<String> insights = new ArrayList<>();
        int maintenance = request.maintenanceCount() == null ? 0 : request.maintenanceCount();
        int available = request.availableCount() == null ? 0 : request.availableCount();
        int inUse = request.inUseCount() == null ? 0 : request.inUseCount();
        if (maintenance > 0) {
            insights.add(maintenance + " asset(s) flagged for maintenance — clear workshop queue before reissue.");
        }
        if (available == 0 && inUse > 0) {
            insights.add("No available stock — plan procurement or reclaim unused devices.");
        }
        if (available > 0) {
            insights.add(available + " available unit(s) ready for assignment from warehouse.");
        }
        if (request.flaggedItems() != null) {
            for (String item : request.flaggedItems()) {
                if (item != null && !item.isBlank()) {
                    insights.add("Review: " + item.trim());
                }
                if (insights.size() >= 4) break;
            }
        }
        if (insights.isEmpty()) {
            insights.add("Inventory counts look balanced — spot-check overdue checkouts.");
        }
        String summary = "Heuristic asset ops tips. AI does not reassign hardware.";
        return new AiDtos.AssetsInsightResponse(insights, summary, "heuristic", DISCLAIMER);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? "-" : value.trim();
    }
}
