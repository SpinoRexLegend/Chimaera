package com.chimaera;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

@Entity @Table(name = "user_resumes")
class UserResume {
    @Id @Column(name = "user_id") UUID userId;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMBLOB") byte[] content;
    @Column(nullable = false) Instant updatedAt;
    protected UserResume() {}
    UserResume(UUID userId, byte[] content) {
        this.userId = userId; this.content = content; this.updatedAt = Instant.now();
    }
}
interface ResumeRepository extends JpaRepository<UserResume, UUID> {}
record ResumeInfo(boolean available, Instant updatedAt, List<String> suggestedSkills,
                  String suggestedOrganization, String parsingMessage) {}

@RestController @RequestMapping("/api")
class ResumeController {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private final ResumeRepository resumes;
    private final QuestService quests;
    private final ProposalRepository proposals;
    private final SkillRepository skills;
    private final AiClient ai;
    private final AuthProfileService profiles;
    ResumeController(ResumeRepository resumes, QuestService quests, ProposalRepository proposals,
                     SkillRepository skills, AiClient ai, AuthProfileService profiles) {
        this.resumes = resumes; this.quests = quests; this.proposals = proposals; this.skills = skills; this.ai = ai;
        this.profiles = profiles;
    }

    @GetMapping("/inbox/proposals/{id}/sender-resume") @Transactional
    ResponseEntity<byte[]> sender(@PathVariable UUID id) {
        var p = proposals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (p.status() == ProposalStatus.DRAFT || p.receiver() == null || !p.receiver().id().equals(quests.currentUser().id()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        var owner = p.quest().owner();
        if (owner == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume unavailable");
        return download(owner.id());
    }

    static void validate(byte[] content) {
        if (content.length < 5 || content.length > MAX_BYTES || content[0] != '%' || content[1] != 'P'
                || content[2] != 'D' || content[3] != 'F' || content[4] != '-')
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF no larger than 5 MB");
    }

    @GetMapping("/profile/resume/info") @Transactional
    ResumeInfo info() {
        return resumes.findById(quests.currentUser().id()).map(r -> new ResumeInfo(true, r.updatedAt, List.of(), null, null))
                .orElse(new ResumeInfo(false, null, List.of(), null, null));
    }
    @PutMapping(value = "/profile/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional
    ResumeInfo upload(@RequestParam("file") MultipartFile file) throws IOException {
        UUID owner = quests.currentUser().id();
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum size is 5 MB");
        byte[] content = file.getBytes();
        validate(content);
        var resume = resumes.save(new UserResume(owner, content));
        var parsed = parse(resume);
        profiles.applyResumeSuggestions(owner, parsed.suggestedOrganization(), parsed.suggestedSkills());
        return parsed;
    }
    @DeleteMapping("/profile/resume") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void remove() { resumes.deleteById(quests.currentUser().id()); }

    @GetMapping("/profile/resume") @Transactional
    ResponseEntity<byte[]> own() { return download(quests.currentUser().id()); }

    @GetMapping("/quests/{questId}/candidates/{candidateId}/resume") @Transactional
    ResponseEntity<byte[]> candidate(@PathVariable UUID questId, @PathVariable UUID candidateId) {
        quests.get(questId);
        if (candidateId.equals(quests.currentUser().id())) return download(candidateId);
        // Revalidate ownership and current eligibility; no public or guessable resume access.
        boolean eligible = quests.match(questId).stream().anyMatch(c -> c.id().equals(candidateId.toString()));
        if (!eligible) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume unavailable");
        return download(candidateId);
    }
    private ResponseEntity<byte[]> download(UUID id) {
        var resume = resumes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No resume uploaded"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff").body(resume.content);
    }
    private ResumeInfo parse(UserResume resume) {
        try (var document = Loader.loadPDF(resume.content)) {
            String text = new PDFTextStripper().getText(document).replace('\u0000', ' ').trim();
            if (text.isBlank()) return new ResumeInfo(true, resume.updatedAt, List.of(), null,
                    "Resume saved, but no selectable text was found. Scanned PDFs require OCR.");
            var catalog = skills.findAll().stream().filter(Skill::active).map(Skill::name).toList();
            List<String> suggested;
            try {
                var allowed = catalog.stream().collect(java.util.stream.Collectors.toMap(Skill::normalize, value -> value, (a, b) -> a));
                suggested = ai.analyse(text.substring(0, Math.min(text.length(), 8000)), List.of(), catalog).capabilities().stream()
                        .map(Skill::normalize).distinct().map(allowed::get).filter(java.util.Objects::nonNull).toList();
            }
            catch (RuntimeException unavailable) { suggested = catalog.stream().filter(skill -> containsPhrase(text, skill)).toList(); }
            String organization = organization(text);
            return new ResumeInfo(true, resume.updatedAt, suggested, organization,
                    suggested.isEmpty() && organization == null
                            ? "Resume saved. No reliable profile suggestions were detected; you can enter them manually."
                            : "Your profile organization and skills were updated from the resume.");
        } catch (IOException | RuntimeException invalid) {
            return new ResumeInfo(true, resume.updatedAt, List.of(), null,
                    "Resume saved, but its text could not be parsed. You can enter profile details manually.");
        }
    }
    private static boolean containsPhrase(String text, String phrase) {
        return Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }
    private static String organization(String text) {
        var match = Pattern.compile("(?im)^(?:organization|company|employer)\\s*[:\\-]\\s*(.{2,180})$").matcher(text);
        if (!match.find()) return null;
        String value = match.group(1).trim();
        return value.length() > 180 ? value.substring(0, 180) : value;
    }
}
