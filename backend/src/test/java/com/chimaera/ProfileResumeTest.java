package com.chimaera;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class ProfileResumeTest {
    @Autowired AuthProfileService profiles;
    @Autowired ResumeController resumes;
    @MockBean QuestService quests;

    ProfileSyncRequest details(String name, String skill) {
        return new ProfileSyncRequest(name, "Bio", "School", List.of(skill), List.of(), "AVAILABLE", "MEMBERS", "UTC");
    }
    @Test void editsSurviveLoginSync() {
        profiles.sync("profile-test", "test@example.test", details("Original", "Java"));
        profiles.save("profile-test", "test@example.test", details("Edited", "Rust"));
        var saved = profiles.sync("profile-test", "test@example.test", details("Original", "Java"));
        assertEquals("Edited", saved.displayName());
        assertEquals(List.of("rust"), profiles.get("profile-test").skills());
    }
    @Test void matchingEmailDoesNotRebindToNewSupabaseSubject() {
        var original = profiles.sync("old-subject", "same@example.test", details("Member", "Java"));
        var separate = profiles.sync("new-subject", "SAME@example.test", details("Separate", "Rust"));
        assertNotEquals(original.id(), separate.id());
        assertEquals("Member", profiles.get("old-subject").displayName());
        assertEquals("Separate", profiles.get("new-subject").displayName());
    }
    @Test void privateResumeLifecycleAndEligibility() throws Exception {
        var profile = profiles.sync("resume-test", "resume@example.test", details("Resume", "Rust"));
        var user = mock(UserProfile.class);
        when(user.id()).thenReturn(profile.id());
        when(quests.currentUser()).thenReturn(user);
        assertFalse(resumes.info().available());
        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        resumes.upload(new MockMultipartFile("file", "resume.pdf", "application/pdf", pdf));
        assertTrue(resumes.info().available());
        assertArrayEquals(pdf, resumes.own().getBody());
        var questId = UUID.randomUUID();
        when(user.id()).thenReturn(UUID.randomUUID());
        when(quests.match(questId)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> resumes.candidate(questId, profile.id()));
        when(quests.match(questId)).thenReturn(List.of(new CandidateView(profile.id().toString(), "Resume", 1, List.of())));
        assertArrayEquals(pdf, resumes.candidate(questId, profile.id()).getBody());
        resumes.remove();
        assertFalse(resumes.info().available());
    }
    @Test void rejectsInvalidAndOversizedFiles() {
        assertThrows(ResponseStatusException.class, () -> ResumeController.validate("not pdf".getBytes()));
        assertThrows(ResponseStatusException.class, () -> ResumeController.validate(new byte[ResumeController.MAX_BYTES + 1]));
    }
    @Test void resumeParsingUsesEachResumeInsteadOfStaleCatalogValues() throws Exception {
        profiles.sync("resume-catalog", "catalog@example.test", details("Catalog", "Rust"));
        var profile = profiles.sync("resume-parse", "parse@example.test", details("Parser", "Java"));
        var user = mock(UserProfile.class);
        when(user.id()).thenReturn(profile.id());
        when(quests.currentUser()).thenReturn(user);

        var parsed = resumes.upload(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                resumePdf("Company: Example Labs", "Skills: Kotlin, Figma")));
        assertEquals("Example Labs", parsed.suggestedOrganization());
        assertEquals(Set.of("kotlin", "figma"), parsed.suggestedSkills().stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet()));
        var first = profiles.get("resume-parse");
        assertEquals("Example Labs", first.institution());
        assertEquals(Set.of("kotlin", "figma"), new HashSet<>(first.skills()));

        var replacement = resumes.upload(new MockMultipartFile("file", "replacement.pdf", "application/pdf",
                resumePdf("Employer: Nova Works", "Technical Skills: Go; Blender")));
        assertEquals("Nova Works", replacement.suggestedOrganization());
        assertEquals(Set.of("go", "blender"), replacement.suggestedSkills().stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet()));
        var updated = profiles.get("resume-parse");
        assertEquals("Nova Works", updated.institution());
        assertEquals(Set.of("go", "blender"), new HashSet<>(updated.skills()));
        assertTrue(resumes.info().available());
    }

    private byte[] resumePdf(String... lines) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -20);
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
