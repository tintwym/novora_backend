package prod.tint_wym.novora_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.AiDtos;
import prod.tint_wym.novora_backend.entity.Announcement;
import prod.tint_wym.novora_backend.entity.Holiday;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.entity.OtPolicy;
import prod.tint_wym.novora_backend.repository.AnnouncementRepository;
import prod.tint_wym.novora_backend.repository.HolidayRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.repository.OtPolicyRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

/**
 * Employee-facing HR policy Q&A grounded only in the tenant's own HR settings
 * (leave types, overtime policies, holidays, announcements). No personal data is sent to the model.
 */
@Service
public class PolicyAssistantService {

    private static final int MAX_HOLIDAYS = 30;
    private static final int MAX_ANNOUNCEMENTS = 15;
    private static final int MAX_ANNOUNCEMENT_CHARS = 600;
    private static final long WINDOW_MILLIS = 60L * 60L * 1000L;
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "how", "many", "much", "what", "when", "where", "who", "why",
            "can", "does", "did", "have", "has", "with", "this", "that", "our", "your", "you", "any",
            "about", "there", "will", "should", "could", "would", "get", "got", "per", "from", "into", "may");

    private record Entry(String label, String text) {}

    private final AiService aiService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final OtPolicyRepository otPolicyRepository;
    private final HolidayRepository holidayRepository;
    private final AnnouncementRepository announcementRepository;
    private final int maxPerHour;
    private final Map<String, long[]> usage = new ConcurrentHashMap<>();

    public PolicyAssistantService(
            AiService aiService,
            LeaveTypeRepository leaveTypeRepository,
            OtPolicyRepository otPolicyRepository,
            HolidayRepository holidayRepository,
            AnnouncementRepository announcementRepository,
            @Value("${app.ai.policy-qa.max-per-hour:20}") int maxPerHour
    ) {
        this.aiService = aiService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.otPolicyRepository = otPolicyRepository;
        this.holidayRepository = holidayRepository;
        this.announcementRepository = announcementRepository;
        this.maxPerHour = Math.max(1, maxPerHour);
    }

    public AiDtos.PolicyQaResponse ask(String rawQuestion) {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        enforceRateLimit(orgId + ":" + (auth == null ? "anonymous" : auth.getName()));

        List<Entry> entries = collectKnowledge(roleNames(auth));
        if (entries.isEmpty()) {
            return new AiDtos.PolicyQaResponse(
                    "Your company hasn't published any HR policies in Novora yet. "
                            + "Please file a helpdesk ticket and HR will help you.",
                    List.of(), "heuristic", AiService.policyDisclaimer());
        }

        List<String> sources = entries.stream()
                .map(e -> e.label().substring(0, e.label().indexOf(':')))
                .distinct()
                .toList();
        if (aiService.modelAvailable()) {
            StringBuilder knowledge = new StringBuilder();
            for (Entry e : entries) {
                knowledge.append("- ").append(e.text()).append('\n');
            }
            AiDtos.PolicyQaResponse ai = aiService.policyAnswer(question, knowledge.toString(), sources);
            if (ai != null) {
                return ai;
            }
        }
        return keywordAnswer(question, entries);
    }

    private void enforceRateLimit(String key) {
        long now = System.currentTimeMillis();
        long[] window = usage.compute(key, (k, w) -> {
            if (w == null || now - w[0] >= WINDOW_MILLIS) {
                return new long[] {now, 1};
            }
            w[1]++;
            return w;
        });
        if (window[1] > maxPerHour) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You've reached the assistant limit for this hour. Please try again later or file a ticket.");
        }
        if (usage.size() > 10_000) {
            usage.entrySet().removeIf(e -> now - e.getValue()[0] >= WINDOW_MILLIS);
        }
    }

    private List<Entry> collectKnowledge(Set<String> roles) {
        List<Entry> entries = new ArrayList<>();

        for (LeaveType lt : leaveTypeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc()) {
            StringBuilder sb = new StringBuilder("Leave type ").append(lt.getName());
            if (lt.getCode() != null && !lt.getCode().isBlank()) {
                sb.append(" (").append(lt.getCode()).append(')');
            }
            sb.append(": ").append(lt.getDaysAllowed()).append(" days per year, ")
                    .append(lt.isPaid() ? "paid" : "unpaid");
            if (lt.isCarryForward()) {
                sb.append(", unused days carry forward up to ").append(lt.getMaxCarryDays()).append(" days");
            } else {
                sb.append(", no carry forward");
            }
            sb.append('.');
            appendNote(sb, lt.getDescription());
            entries.add(new Entry("Leave: " + lt.getName(), sb.toString()));
        }

        for (OtPolicy ot : otPolicyRepository.findAllByOrderByNameAsc()) {
            if (!ot.isActive()) continue;
            StringBuilder sb = new StringBuilder("Overtime policy ").append(ot.getName())
                    .append(": overtime starts after ").append(plain(ot.getDailyThresholdHours()))
                    .append(" hours per day; weekday rate ").append(plain(ot.getWeekdayMultiplier()))
                    .append("x, weekend rate ").append(plain(ot.getWeekendMultiplier()))
                    .append("x, public holiday rate ").append(plain(ot.getHolidayMultiplier())).append("x; ")
                    .append(ot.isRequiresApproval() ? "requires approval." : "no approval needed.");
            appendNote(sb, ot.getNotes());
            entries.add(new Entry("Overtime: " + ot.getName(), sb.toString()));
        }

        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusYears(1);
        holidayRepository.findAllByOrderByHolidayDateAsc().stream()
                .filter(h -> h.getHolidayDate() != null
                        && !h.getHolidayDate().isBefore(today)
                        && !h.getHolidayDate().isAfter(horizon))
                .limit(MAX_HOLIDAYS)
                .forEach(h -> entries.add(new Entry("Holiday: " + h.getName(), holidayText(h))));

        LocalDateTime now = LocalDateTime.now();
        announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> a.getPublishedAt() == null || !a.getPublishedAt().isAfter(now))
                .filter(a -> a.getExpiresAt() == null || a.getExpiresAt().isAfter(now))
                .filter(a -> a.getDepartment() == null)
                .filter(a -> visibleToRoles(a, roles))
                .sorted(Comparator.comparing(Announcement::isPinned).reversed())
                .limit(MAX_ANNOUNCEMENTS)
                .forEach(a -> entries.add(new Entry("Announcement: " + a.getTitle(), announcementText(a))));

        return entries;
    }

    private static boolean visibleToRoles(Announcement a, Set<String> roles) {
        String target = a.getTargetRole();
        if (target == null || target.isBlank()) return true;
        String normalized = target.trim().toUpperCase(Locale.US);
        return "ALL".equals(normalized) || "EVERYONE".equals(normalized) || roles.contains(normalized);
    }

    private static Set<String> roleNames(Authentication auth) {
        Set<String> roles = new LinkedHashSet<>();
        if (auth == null) return roles;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String name = ga.getAuthority();
            if (name == null) continue;
            roles.add(name.startsWith("ROLE_") ? name.substring(5) : name);
        }
        return roles;
    }

    private static String holidayText(Holiday h) {
        StringBuilder sb = new StringBuilder("Holiday ").append(h.getName())
                .append(" on ").append(h.getHolidayDate());
        if (h.getType() != null && !h.getType().isBlank()) {
            sb.append(" (").append(h.getType().trim()).append(')');
        }
        sb.append('.');
        appendNote(sb, h.getDescription());
        return sb.toString();
    }

    private static String announcementText(Announcement a) {
        String content = a.getContent() == null ? "" : a.getContent().replaceAll("\\s+", " ").trim();
        if (content.length() > MAX_ANNOUNCEMENT_CHARS) {
            content = content.substring(0, MAX_ANNOUNCEMENT_CHARS - 3) + "...";
        }
        return "Announcement \"" + a.getTitle() + "\": " + content;
    }

    private static void appendNote(StringBuilder sb, String note) {
        if (note != null && !note.isBlank()) {
            sb.append(' ').append(note.replaceAll("\\s+", " ").trim());
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static AiDtos.PolicyQaResponse keywordAnswer(String question, List<Entry> entries) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : question.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
            if (token.length() >= 3 && !STOPWORDS.contains(token)) {
                terms.add(token.endsWith("s") && token.length() > 4 ? token.substring(0, token.length() - 1) : token);
            }
        }
        record Scored(Entry entry, int score) {}
        List<Scored> matches = entries.stream()
                .map(e -> {
                    String hay = e.text().toLowerCase(Locale.US);
                    int score = 0;
                    for (String t : terms) {
                        if (hay.contains(t)) score++;
                    }
                    return new Scored(e, score);
                })
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(3)
                .toList();

        if (matches.isEmpty()) {
            return new AiDtos.PolicyQaResponse(
                    "I couldn't find that in your company's HR settings. "
                            + "Please file a helpdesk ticket and HR will get back to you.",
                    List.of(), "heuristic", AiService.policyDisclaimer());
        }
        StringBuilder answer = new StringBuilder("Here's what I found in your company's HR settings:");
        List<String> sources = new ArrayList<>();
        for (Scored s : matches) {
            answer.append("\n- ").append(s.entry().text());
            sources.add(s.entry().label());
        }
        return new AiDtos.PolicyQaResponse(answer.toString(), sources, "heuristic", AiService.policyDisclaimer());
    }
}
