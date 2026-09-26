package com.chimaera;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class QuestFlowTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Test void rejectsAnonymousQuestCreation() throws Exception {
        var request = new CreateQuest("Hackathon team", "Need a frontend collaborator", "Build our React UI", LocalDate.now().plusDays(5), 4, List.of("react"));
        mvc.perform(post("/api/quests").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
