package talan.pfe.rulengine.services.llm;

import java.util.List;

public interface LlmClient {
    String generate(String systemPrompt, String userPrompt);

    String chat(String systemPrompt, List<Message> history, String userPrompt);

    record Message(String role, String content) {}
}
