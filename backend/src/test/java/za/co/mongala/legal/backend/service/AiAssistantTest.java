package za.co.mongala.legal.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Local AI help chat: the question is scrubbed before it reaches the model. */
class AiAssistantTest {

    @Test
    @DisplayName("AI chat: case references and case, client and party words are replaced before the model sees them")
    void scrubRemovesCasePrivateWords() {
        List<String> fields = List.of("Dlamini v Nedbank Ltd", "C-2026-019", "Sibongile Dlamini", "Nedbank Ltd");
        String out = AiAssistant.scrub("What is the case for Sibongile Dlamini on C-2026-019 against Nedbank?", fields);
        assertFalse(out.contains("Dlamini"));
        assertFalse(out.contains("Nedbank"));
        assertFalse(out.contains("C-2026-019"));
        assertEquals("What is the case for [private] [private] on [private] against [private]?", out);
    }

    @Test
    @DisplayName("AI chat: general questions pass through unchanged")
    void scrubKeepsGeneralQuestions() {
        assertEquals("Explain what a summons is.", AiAssistant.scrub("Explain what a summons is.", List.of("Dlamini v Nedbank Ltd")));
    }

    @Test
    @DisplayName("AI chat: off unless the local AI server is configured")
    void offByDefault() {
        if (System.getenv("LCE_OLLAMA_URL") == null) {
            assertFalse(AiAssistant.configured());
        }
    }
}
