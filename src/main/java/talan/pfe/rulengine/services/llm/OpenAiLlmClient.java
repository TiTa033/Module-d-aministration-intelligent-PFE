package talan.pfe.rulengine.services.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiLlmClient implements LlmClient {

    private final ChatModel chatModel;

    @Value("${llm.model}")
    private String modelName;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, List.of(), userPrompt);
    }

    @Override
    public String chat(String systemPrompt, List<Message> history, String userPrompt) {
        try {
            log.debug("LLM call — history={} messages", history.size());

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));

            for (Message message : history) {
                if ("assistant".equalsIgnoreCase(message.role())) {
                    messages.add(new AssistantMessage(message.content()));
                } else {
                    messages.add(new UserMessage(message.content()));
                }
            }
            messages.add(new UserMessage(userPrompt));

            var response = chatModel.call(new Prompt(messages));
            var result  = response == null ? null : response.getResult();
            if (result == null) throw new BadRequestException("LLM returned a null result.");

            var output = result.getOutput();
            if (output == null) throw new BadRequestException("LLM returned a null output.");

            String content = output.getText();
            if (content == null || content.isBlank()) throw new BadRequestException("LLM returned an empty response.");

            log.debug("LLM response: {} characters", content.length());
            return content;

        } catch (BadRequestException e) {
            log.error("LLM BadRequestException: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("LLM call failed", e);
            throw new BadRequestException(buildErrorMessage(e));
        }
    }

    private String buildErrorMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();

        String full = (e.getMessage() != null ? e.getMessage() : "")
                + (root.getMessage() != null ? " " + root.getMessage() : "");

        if (full.contains("401") || full.contains("Unauthorized") || full.contains("invalid_api_key")) {
            return "LLM — Clé API Groq invalide (401). Vérifiez 'spring.ai.openai.api-key' dans application.properties.";
        }
        if (full.contains("429") || full.contains("Too Many Requests") || full.contains("rate_limit")) {
            return "LLM — Quota Groq dépassé (429). Réessayez dans quelques instants.";
        }
        if (full.contains("404") || full.contains("Not Found") || full.contains("model_not_found")) {
            return "LLM — Modèle introuvable ou bloqué. Vérifiez 'llm.model' dans application.properties (modèle actuel : " + modelName + ").";
        }
        if (full.contains("400") || full.contains("Bad Request")) {
            return "LLM — Requête invalide (400). Vérifiez la configuration dans application.properties (base-url=" + baseUrl + ", model=" + modelName + ").";
        }

        String detail = root.getMessage() != null ? root.getMessage() : e.getClass().getSimpleName();
        if (detail.length() > 200) detail = detail.substring(0, 200) + "…";
        return "LLM indisponible : " + detail;
    }
}
