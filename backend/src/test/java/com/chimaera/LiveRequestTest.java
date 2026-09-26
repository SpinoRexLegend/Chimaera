package com.chimaera;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class LiveRequestTest {
    @Autowired AuthProfileService profiles;
    @Autowired QuestService service;
    @Autowired ProposalRepository proposals;
    @MockBean AiClient ai;

    void login(String subject) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256").subject(subject).build()));
    }
    ProfileView profile(String subject, String skill, String visibility) {
        return profiles.sync(subject, subject + "@example.test", new ProfileSyncRequest(subject, "", "", List.of(skill), List.of(), "AVAILABLE", visibility, "UTC"));
    }
    @Test void registeredUsersRouteToRecipientAndOtherUsersCannotSend() {
        try {
            var owner = profile("owner", "Java", "MEMBERS");
            var receiver = profile("receiver", "Rust", "MEMBERS");
            profile("private", "Rust", "PRIVATE");
            login("owner");
            var quest = service.create(new CreateQuest("Build", "Build", "Need Rust", LocalDate.now().plusDays(3), 3, List.of("Rust")));
            when(ai.rank(anyList(), anyList())).thenAnswer(call -> {
                List<Map<String,Object>> candidates = call.getArgument(1);
                assertEquals(1, candidates.size());
                assertEquals(receiver.id().toString(), candidates.get(0).get("id"));
                return List.of(new CandidateView(receiver.id().toString(), "receiver", 1, List.of("Rust")));
            });
            var draft = service.draft(quest.id(), new DraftRequest(receiver.id().toString(), "forged"));
            assertFalse(draft.message().contains("forged"));
            login("receiver");
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.send(draft.id(), true));
            login("owner");
            assertEquals(ProposalStatus.SENT, service.send(draft.id(), true).status());
            assertEquals(1, proposals.findByReceiver_IdOrderByCreatedAtDesc(receiver.id()).size());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
