package com.chimaera;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@SpringBootApplication
public class ChimaeraApplication {
    public static void main(String[] args) { SpringApplication.run(ChimaeraApplication.class, args); }
}

enum QuestStatus { DRAFT, MATCHING }
enum ProposalStatus { DRAFT, SENT, ACCEPTED, DECLINED }

@Entity @Table(name = "quests")
class Quest {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_id") private UserProfile owner;
    void owner(UserProfile user) { owner = user; }
    UserProfile owner() { return owner; }
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 600) private String publicSummary;
    @Column(nullable = false, length = 8000) private String privateDescription;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private QuestStatus status = QuestStatus.DRAFT;
    @Column(nullable = false) private LocalDate deadline;
    @Column(nullable = false) private int maxMembers;
    @ElementCollection @CollectionTable(name = "quest_requirements", joinColumns = @JoinColumn(name = "quest_id"))
    @Column(name = "skill", nullable = false) private Set<String> requirements = new LinkedHashSet<>();
    protected Quest() {}
    Quest(CreateQuest request) {
        title = request.title(); publicSummary = request.publicSummary(); privateDescription = request.privateDescription();
        deadline = request.deadline(); maxMembers = request.maxMembers();
        requirements = new LinkedHashSet<>(request.desiredSkills() == null ? List.of() : request.desiredSkills());
    }
    UUID id() { return id; }
    String title() { return title; }
    String publicSummary() { return publicSummary; }
    String privateDescription() { return privateDescription; }
    QuestStatus status() { return status; }
    LocalDate deadline() { return deadline; }
    int maxMembers() { return maxMembers; }
    Set<String> requirements() { return requirements; }
    void replaceRequirements(List<String> skills) { requirements = new LinkedHashSet<>(skills); }
    void beginMatching() { status = QuestStatus.MATCHING; }
}

@Entity @Table(name = "collaboration_proposals")
class Proposal {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(optional = false) private Quest quest;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "receiver_id") private UserProfile receiver;
    @Column(nullable = false) private String candidateId;
    @Column(nullable = false, length = 2000) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProposalStatus status = ProposalStatus.DRAFT;
    private Instant sentAt;
    private Instant respondedAt;
    private Instant receiverDeletedAt;
    private Instant createdAt = Instant.now();
    protected Proposal() {}
    Proposal(Quest quest, UserProfile receiver, String candidateId, String message) {
        this.quest = quest; this.receiver = receiver; this.candidateId = candidateId; this.message = message;
    }
    UUID id() { return id; }
    Quest quest() { return quest; }
    UserProfile receiver() { return receiver; }
    String candidateId() { return candidateId; }
    String message() { return message; }
    ProposalStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant sentAt() { return sentAt; }
    Instant respondedAt() { return respondedAt; }
    boolean hiddenFromReceiver() { return receiverDeletedAt != null; }
    void hideFromReceiver() { receiverDeletedAt = Instant.now(); }
    void respond(ProposalStatus decision) {
        if (decision != ProposalStatus.ACCEPTED && decision != ProposalStatus.DECLINED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose accept or decline");
        if (status == decision) return;
        if (status != ProposalStatus.SENT) throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been answered");
        status = decision; respondedAt = Instant.now();
    }
    void send() {
        if (status == ProposalStatus.SENT) return;
        if (status != ProposalStatus.DRAFT) throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been answered");
        status = ProposalStatus.SENT; sentAt = Instant.now();
    }
}

interface QuestRepository extends JpaRepository<Quest, UUID> {
    List<Quest> findByOwner_Id(UUID ownerId);
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select q from Quest q where q.id = :id")
    Optional<Quest> lock(@org.springframework.data.repository.query.Param("id") UUID id);
}
interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findByReceiver_IdOrderByCreatedAtDesc(UUID receiverId);
    List<Proposal> findByQuest_Owner_IdOrderByCreatedAtDesc(UUID ownerId);
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Proposal p where p.id = :id")
    Optional<Proposal> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}

record CreateQuest(@NotBlank @Size(max=120) String title, @NotBlank @Size(max=600) String publicSummary,
                   @NotBlank @Size(max=8000) String privateDescription, @Future LocalDate deadline,
                   @Min(2) @Max(20) int maxMembers,
                   @Size(max=30) List<@NotBlank @Size(max=120) String> desiredSkills) {}
record QuestView(UUID id, String title, String publicSummary, QuestStatus status, LocalDate deadline, int maxMembers, List<String> requirements) {}
record AnalysisView(List<String> capabilities, String summary, @JsonAlias("requires_owner_confirmation") boolean requiresOwnerConfirmation) {}
record CandidateView(String id, String name, @JsonAlias("match_score") double matchScore, List<String> reasons) {}
record DraftRequest(@NotBlank String candidateId, @NotBlank String candidateName) {}
record SendRequest(boolean approved) {}
record ProposalView(UUID id, UUID questId, String candidateId, String message, ProposalStatus status) {}

@Component
class AiClient {
    private final RestClient client;
    private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(4, true);
    AiClient(@Value("${chimaera.ai-service-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(15000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    private <T> T limited(java.util.function.Supplier<T> action) {
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Matching is busy. Please retry shortly.");
        try { return action.get(); } finally { slots.release(); }
    }
    AnalysisView analyse(String text, List<String> explicit, List<String> catalog) {
        return limited(() -> client.post().uri("/analyse").body(Map.of("text", text, "explicit_skills", explicit, "skill_catalog", catalog)).retrieve().body(AnalysisView.class));
    }
    List<CandidateView> rank(List<String> required, List<Map<String,Object>> candidates) {
        return limited(() -> client.post().uri("/rank").body(Map.of("required_skills", required, "candidates", candidates)).retrieve()
                .body(new ParameterizedTypeReference<List<CandidateView>>() {}));
    }
    Map<String,Object> plan(PlanningRequest request, List<Map<String,Object>> employees) {
        return limited(() -> client.post().uri("/plan").body(Map.of("tasks",request.tasks(),"team_size",request.teamSize(),"candidates",employees))
            .retrieve().body(new ParameterizedTypeReference<Map<String,Object>>() {}));
    }
}

@Service
class QuestService {
    private final AuthIdentityRepository identities;
    private final UserSkillRepository userSkills;
    private final SkillRepository skills;
    private final QuestRepository quests; private final ProposalRepository proposals; private final UserProfileRepository users; private final AiClient ai;
    QuestService(QuestRepository quests, ProposalRepository proposals, UserProfileRepository users, AiClient ai, AuthIdentityRepository identities, UserSkillRepository userSkills, SkillRepository skills) {
        this.identities = identities; this.userSkills = userSkills; this.skills = skills;
        this.quests = quests; this.proposals = proposals; this.users = users; this.ai = ai;
    }
    UserProfile currentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
        return identities.findByProviderAndSubject("supabase", jwt.getSubject()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Complete profile setup first")).user();
    }
    @Transactional Quest create(CreateQuest request) { Quest quest = new Quest(request); quest.owner(currentUser()); return quests.save(quest); }
    Quest get(UUID id) {
        Quest quest = quests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quest not found"));
        if (quest.owner() == null || !quest.owner().id().equals(currentUser().id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quest not found");
        return quest;
    }
    @Transactional AnalysisView analyse(UUID id) {
        Quest quest = get(id);
        try {
            AnalysisView result = ai.analyse(quest.privateDescription(), List.copyOf(quest.requirements()), skills.findAll().stream().map(Skill::name).toList());
            quest.replaceRequirements(result.capabilities()); return result;
        } catch (RuntimeException unavailable) {
            return new AnalysisView(List.copyOf(quest.requirements()), "AI unavailable; using owner-provided requirements.", true);
        }
    }
    @Transactional List<CandidateView> match(UUID id) {
        Quest quest = get(id); quest.beginMatching();
        try { return ai.rank(List.copyOf(quest.requirements()), candidates()); }
        catch (RuntimeException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Matching model unavailable. Please retry.");
        }
    }
    @Transactional ProposalView draft(UUID questId, DraftRequest request) {
        Quest quest = get(questId);
        CandidateView selected = match(questId).stream().limit(3).filter(c -> c.id().equals(request.candidateId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of the current top three eligible matches"));
        String message = "Hi " + selected.name() + ", we are building " + quest.title() + ". Your capabilities appear relevant to: "
                + String.join(", ", quest.requirements()) + ". Would you like to review the Quest and collaborate?";
        UserProfile receiver = users.findById(UUID.fromString(selected.id())).orElseThrow();
        return view(proposals.save(new Proposal(quest, receiver, request.candidateId(), message)));
    }
    @Transactional ProposalView send(UUID proposalId, boolean approved) {
        if (!approved) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit owner approval is required");
        Proposal proposal = proposals.findForUpdate(proposalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
        get(proposal.quest().id());
        proposal.send(); return view(proposal);
    }
    private ProposalView view(Proposal p) { return new ProposalView(p.id(), p.quest().id(), p.candidateId(), p.message(), p.status()); }
    private List<Map<String,Object>> candidates() {
        UUID requester = currentUser().id();
        Map<UUID, List<String>> catalog = new LinkedHashMap<>();
        Map<UUID, UserProfile> profiles = new LinkedHashMap<>();
        for (UserSkill link : userSkills.findEligible(requester)) {
            UserProfile user = link.user();
            profiles.put(user.id(), user);
            catalog.computeIfAbsent(user.id(), key -> new ArrayList<>()).add(link.skill().name());
        }
        return profiles.values().stream().map(user -> Map.<String,Object>of("id", user.id().toString(), "name", user.displayName(), "skills", catalog.get(user.id()))).toList();
    }
}

@RestController @RequestMapping("/api")
class ApiController {
    private final QuestService service;
    ApiController(QuestService service) { this.service = service; }
    @PostMapping("/quests") @ResponseStatus(HttpStatus.CREATED)
    QuestView create(@Valid @RequestBody CreateQuest request) { return view(service.create(request)); }
    @PostMapping("/quests/{id}/analyse") AnalysisView analyse(@PathVariable UUID id) { return service.analyse(id); }
    @PostMapping("/quests/{id}/match") List<CandidateView> match(@PathVariable UUID id) { return service.match(id); }
    @PostMapping("/quests/{id}/proposals/draft") ProposalView draft(@PathVariable UUID id, @Valid @RequestBody DraftRequest request) { return service.draft(id, request); }
    @PostMapping("/proposals/{id}/send") ProposalView send(@PathVariable UUID id, @RequestBody SendRequest request) { return service.send(id, request.approved()); }
    private QuestView view(Quest q) { return new QuestView(q.id(), q.title(), q.publicSummary(), q.status(), q.deadline(), q.maxMembers(), List.copyOf(q.requirements())); }
}
