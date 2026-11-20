package za.co.mongala.legal.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.mongala.legal.backend.service.ChatbotService.Intent;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure intent matching for the help chat robot (no database needed).
 */
class ChatbotServiceTest {

    @Test
    @DisplayName("Chat: next court date question maps to the hearing answer")
    void nextCourtDate() {
        assertEquals(Intent.NEXT_HEARING, ChatbotService.classify("When is my next court date?"));
    }

    @Test
    @DisplayName("Chat: amount owed and trust balance map to the balance answer")
    void balance() {
        assertEquals(Intent.BALANCE, ChatbotService.classify("How much do I owe?"));
        assertEquals(Intent.BALANCE, ChatbotService.classify("What is my trust balance?"));
    }

    @Test
    @DisplayName("Chat: payment, messaging, rating and documents questions get help text")
    void faqTopics() {
        assertEquals(Intent.HOW_TO_PAY, ChatbotService.classify("How do I pay my invoice?"));
        assertEquals(Intent.MESSAGES_HELP, ChatbotService.classify("How do I message my attorney?"));
        assertEquals(Intent.RATING_HELP, ChatbotService.classify("Rate my lawyer"));
        assertEquals(Intent.DOCUMENTS_HELP, ChatbotService.classify("Where do I download my documents?"));
        assertEquals(Intent.TRUST_INFO, ChatbotService.classify("What is trust?"));
    }

    @Test
    @DisplayName("Chat: locked-out and password questions map to the login rule")
    void login() {
        assertEquals(Intent.LOGIN_HELP, ChatbotService.classify("I forgot my password and I'm locked out"));
    }

    @Test
    @DisplayName("Chat: case reference and status questions map to the status answer")
    void caseStatus() {
        assertEquals(Intent.CASE_STATUS, ChatbotService.classify("What is the status of C-2026-001?"));
        assertEquals(Intent.DOCUMENT_COUNT, ChatbotService.classify("How many documents do I have?"));
    }

    @Test
    @DisplayName("Chat: greetings give help, blank input gives help, unrelated text is unknown")
    void greetingsAndFallback() {
        assertEquals(Intent.HELP, ChatbotService.classify("hello"));
        assertEquals(Intent.HELP, ChatbotService.classify(""));
        assertEquals(Intent.HELP, ChatbotService.classify(null));
        assertEquals(Intent.UNKNOWN, ChatbotService.classify("Tell me a joke about penguins"));
    }

    @Test
    @DisplayName("Chat: how-to-use questions get the system guide, case names are not treated as unknown text")
    void howToUseAndCaseNames() {
        assertEquals(Intent.HELP, ChatbotService.classify("how to use the system."));
        assertEquals(Intent.HELP, ChatbotService.classify("How do I use this?"));
        // A plain case name is routed to the case lookup, not the "did not understand" reply.
        assertEquals(Intent.UNKNOWN, ChatbotService.classify("Dlamini v Nedbank Ltd"));
        assertEquals(true, ChatbotService.looksLikeCaseName("Dlamini v Nedbank Ltd"));
        assertEquals(false, ChatbotService.looksLikeCaseName("Tell me a joke"));
    }

    @Test
    @DisplayName("Chat: search phrases map to the search rule, specific questions keep their own rule")
    void searchPhrases() {
        assertEquals(Intent.SEARCH, ChatbotService.classify("search Dlamini"));
        assertEquals(Intent.SEARCH, ChatbotService.classify("search hearing"));
        assertEquals(Intent.SEARCH, ChatbotService.classify("find Nkosi v ABSA"));
        assertEquals(Intent.NEXT_HEARING, ChatbotService.classify("find my court date"));
        assertEquals(java.util.List.of("dlamini", "nedbank"), ChatbotService.searchTokens("search Dlamini Nedbank"));
    }

    @Test
    @DisplayName("Chat: internet questions drop case references, party names and digits before anything leaves the system")
    void internetQuestionsAreScrubbed() {
        java.util.List<String> private_ = java.util.List.of("Dlamini v Nedbank Ltd", "C-2026-019", "Sibongile Dlamini", "Nedbank Ltd");
        assertEquals(java.util.List.of("capital", "gauteng"),
                ChatbotService.safeWebWords("What is the capital of Gauteng?", private_));
        assertEquals(java.util.List.of("interest", "rates"),
                ChatbotService.safeWebWords("Dlamini v Nedbank C-2026-019 interest rates", private_));
        // Too little left to search safely: nothing is sent.
        assertEquals(null, ChatbotService.safeWebWords("Sibongile Dlamini C-2026-019", private_));
    }

    @Test
    @DisplayName("Chat: words that only contain a keyword do not match it (power is not owe, generate is not rate)")
    void wholeWordMatching() {
        assertEquals(Intent.UNKNOWN, ChatbotService.classify("Power outage"));
        assertEquals(Intent.UNKNOWN, ChatbotService.classify("Please generate a poem"));
    }
}
