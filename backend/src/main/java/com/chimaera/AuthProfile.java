package com.chimaera;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.*;

@Entity
@Table(name = "users")
class UserProfile {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, length = 120) private String displayName;
    @Column(length = 1000) private String bio;
    @Column(length = 180) private String institution;
    @Column(nullable = false, length = 32) private String availabilityStatus = "AVAILABLE";
    @Column(nullable = false, length = 32) private String profileVisibility = "MEMBERS";
    @Column(nullable = false, length = 32) private String status = "ACTIVE";
    @Column(nullable = false, length = 64) private String timezone = "UTC";
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "interest_tag", nullable = false, length = 100)
    private Set<String> interests = new LinkedHashSet<>();

    protected UserProfile() {}
    UserProfile(ProfileSyncRequest request) { update(request); }
    void update(ProfileSyncRequest request) {
        displayName = request.displayName().trim();
        bio = clean(request.bio());
        institution = clean(request.institution());
        availabilityStatus = normalizedChoice(request.availabilityStatus(), "AVAILABLE");
        profileVisibility = normalizedChoice(request.profileVisibility(), "MEMBERS");
        timezone = clean(request.timezone()) == null ? "UTC" : request.timezone().trim();
        interests = normalizedSet(request.interests());
    }
    UUID id() { return id; }
    boolean active() { return "ACTIVE".equals(status); }
    String displayName() { return displayName; }
    String bio() { return bio; }
    String institution() { return institution; }
    String availabilityStatus() { return availabilityStatus; }
    String profileVisibility() { return profileVisibility; }
    String timezone() { return timezone; }
    Set<String> interests() { return interests; }
    void applyResumeOrganization(String value) {
        String parsed = clean(value);
        if (parsed != null) institution = parsed;
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalizedChoice(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
    private static Set<String> normalizedSet(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().map(String::trim).filter(v -> !v.isBlank())
                .map(v -> v.toLowerCase(Locale.ROOT)).limit(20).forEach(result::add);
        return result;
    }
}

@Entity
@Table(name = "auth_identities", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "subject"}))
class AuthIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private UserProfile user;
    @Column(nullable = false, length = 32) private String provider;
    @Column(nullable = false, length = 160) private String subject;
    @Column(length = 254) private String emailNormalized;
    protected AuthIdentity() {}
    AuthIdentity(UserProfile user, String subject, String email) {
        this.user = user; this.provider = "supabase"; this.subject = subject;
        this.emailNormalized = email == null ? null : email.toLowerCase(Locale.ROOT);
    }
    UserProfile user() { return user; }
    String email() { return emailNormalized; }
    void rebindSubject(String value) { subject = value; }
}

@Entity
@Table(name = "skills")
class Skill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 120) private String normalizedName;
    @Column(nullable = false, length = 120) private String displayName;
    @Column(length = 80) private String category;
    @Column(nullable = false) private boolean active = true;
    protected Skill() {}
    Skill(String value) { normalizedName = normalize(value); displayName = value.trim(); category = "SELF_DECLARED"; }
    Long id() { return id; }
    String name() { return displayName; }
    boolean active() { return active; }
    static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
}

@Embeddable
class UserSkillId implements Serializable {
    private UUID userId;
    private Long skillId;
    protected UserSkillId() {}
    UserSkillId(UUID userId, Long skillId) { this.userId = userId; this.skillId = skillId; }
    @Override public boolean equals(Object other) {
        return other instanceof UserSkillId id && Objects.equals(userId, id.userId) && Objects.equals(skillId, id.skillId);
    }
    @Override public int hashCode() { return Objects.hash(userId, skillId); }
}

@Entity
@Table(name = "user_skills")
class UserSkill {
    @EmbeddedId private UserSkillId id;
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("userId") @JoinColumn(name = "user_id") private UserProfile user;
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("skillId") @JoinColumn(name = "skill_id") private Skill skill;
    private Integer proficiency = 3;
    @Column(nullable = false, length = 40) private String evidenceType = "SELF_DECLARED";
    @Column(nullable = false) private int evidenceCount = 0;
    protected UserSkill() {}
    UserSkill(UserProfile user, Skill skill) {
        this.user = user; this.skill = skill; this.id = new UserSkillId(user.id(), skill.id());
    }
    Skill skill() { return skill; }
    UserProfile user() { return user; }
}

interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {}
interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {
    Optional<AuthIdentity> findByProviderAndSubject(String provider, String subject);
    Optional<AuthIdentity> findByProviderAndEmailNormalized(String provider, String emailNormalized);
}
interface SkillRepository extends JpaRepository<Skill, Long> {
    Optional<Skill> findByNormalizedName(String normalizedName);
}
interface UserSkillRepository extends JpaRepository<UserSkill, UserSkillId> {
    @org.springframework.data.jpa.repository.Query("select us from UserSkill us join fetch us.user u join fetch us.skill s where u.id <> :requester and u.status = 'ACTIVE' and u.availabilityStatus <> 'UNAVAILABLE' and u.profileVisibility <> 'PRIVATE' and s.active = true")
    List<UserSkill> findEligible(@org.springframework.data.repository.query.Param("requester") UUID requester);
    void deleteByUser(UserProfile user);
    List<UserSkill> findByUser(UserProfile user);
}

record ProfileSyncRequest(
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 1000) String bio,
        @Size(max = 180) String institution,
        List<@NotBlank @Size(max = 120) String> skills,
        List<@NotBlank @Size(max = 100) String> interests,
        @jakarta.validation.constraints.Pattern(regexp = "AVAILABLE|LIMITED|UNAVAILABLE") String availabilityStatus,
        @jakarta.validation.constraints.Pattern(regexp = "MEMBERS|PUBLIC|PRIVATE") String profileVisibility,
        @Size(max = 64) String timezone
) {}

record ProfileView(UUID id, String email, String displayName, String bio, String institution,
                   List<String> skills, List<String> interests, String availabilityStatus,
                   String profileVisibility, String timezone) {}

@org.springframework.stereotype.Service
class AuthProfileService {
    private final UserProfileRepository users;
    private final AuthIdentityRepository identities;
    private final SkillRepository skills;
    private final UserSkillRepository userSkills;
    private final WorkforceRepository workforce;

    AuthProfileService(UserProfileRepository users, AuthIdentityRepository identities,
                       SkillRepository skills, UserSkillRepository userSkills, WorkforceRepository workforce) {
        this.users = users; this.identities = identities; this.skills = skills; this.userSkills = userSkills;
        this.workforce = workforce;
    }

    @Transactional
    ProfileView sync(String subject, String email, ProfileSyncRequest request) {
        var existing = resolveIdentity(subject, email);
        if (existing.isPresent()) {
            var identity = existing.get();
            ensureWorkforce(identity.user());
            return view(identity, identity.user(), userSkills.findByUser(identity.user()));
        }
        return save(subject, email, request);
    }

    @Transactional
    ProfileView get(String subject) {
        var identity = identities.findByProviderAndSubject("supabase", subject).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Profile not found"));
        return view(identity, identity.user(), userSkills.findByUser(identity.user()));
    }

    @Transactional
    ProfileView save(String subject, String email, ProfileSyncRequest request) {
        AuthIdentity identity = resolveIdentity(subject, email).orElse(null);
        UserProfile user;
        if (identity == null) {
            user = users.save(new UserProfile(request));
            identity = identities.save(new AuthIdentity(user, subject, email));
        } else {
            user = identity.user();
            user.update(request);
        }

        userSkills.deleteByUser(user);
        userSkills.flush();
        List<UserSkill> linked = new ArrayList<>();
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        if (request.skills() != null) request.skills().stream().map(String::trim).filter(v -> !v.isBlank())
                .map(Skill::normalize).limit(30).forEach(requested::add);
        for (String value : requested) {
            Skill skill = skills.findByNormalizedName(Skill.normalize(value)).orElseGet(() -> skills.save(new Skill(value)));
            linked.add(new UserSkill(user, skill));
        }
        userSkills.saveAll(linked);
        ensureWorkforce(user);
        return view(identity, user, linked);
    }

    @Transactional
    void applyResumeSuggestions(UUID userId, String organization, List<String> suggestedSkills) {
        var user = users.findById(userId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Profile not found"));
        user.applyResumeOrganization(organization);
        List<UserSkill> linked = new ArrayList<>();
        if (suggestedSkills == null || suggestedSkills.isEmpty()) return;
        userSkills.deleteByUser(user);
        userSkills.flush();
        suggestedSkills.stream().map(String::trim).filter(value -> !value.isBlank())
                .map(Skill::normalize).distinct().limit(30).forEach(value -> {
                    Skill skill = skills.findByNormalizedName(value).orElseGet(() -> skills.save(new Skill(value)));
                    linked.add(new UserSkill(user, skill));
                });
        userSkills.saveAll(linked);
    }

    private void ensureWorkforce(UserProfile user) {
        if (workforce.existsById(user.id())) return;
        var profile = new WorkforceProfile(user.id());
        profile.weeklyHours = 40;
        workforce.save(profile);
    }

    private Optional<AuthIdentity> resolveIdentity(String subject, String email) {
        // The signed JWT subject is the identity key. Email alone must never transfer
        // ownership because aliases and re-registration can otherwise take over data.
        return identities.findByProviderAndSubject("supabase", subject);
    }

    private ProfileView view(AuthIdentity identity, UserProfile user, List<UserSkill> linked) {
        return new ProfileView(user.id(), identity.email(), user.displayName(), user.bio(), user.institution(),
                linked.stream().map(link -> link.skill().name()).toList(), List.copyOf(user.interests()),
                user.availabilityStatus(), user.profileVisibility(), user.timezone());
    }
}

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "chimaera.auth.enabled", havingValue = "true")
class AuthProfileController {
    private final AuthProfileService profiles;
    AuthProfileController(AuthProfileService profiles) { this.profiles = profiles; }

    @PostMapping("/sync")
    ProfileView sync(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileSyncRequest request) {
        return profiles.sync(jwt.getSubject(), jwt.getClaimAsString("email"), request);
    }

    @GetMapping("/profile")
    ProfileView get(@AuthenticationPrincipal Jwt jwt) { return profiles.get(jwt.getSubject()); }

    @PutMapping("/profile")
    ProfileView update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileSyncRequest request) {
        return profiles.save(jwt.getSubject(), jwt.getClaimAsString("email"), request);
    }
}
