package talan.pfe.rulengine.services.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenAiLlmClient implements LlmClient {

    private final ChatModel chatModel;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, List.of(), userPrompt);
    }

    @Override
    public String chat(String systemPrompt, List<Message> history, String userPrompt) {
        try {
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
            var result = response == null ? null : response.getResult();
            var output = result == null ? null : result.getOutput();
            String content = output == null ? null : output.getText();
            if (content == null || content.isBlank()) {
                throw new BadRequestException("LLM returned an empty response.");
            }
            return content;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Spring AI call failed: " + e.getMessage());
        }
    }
}
