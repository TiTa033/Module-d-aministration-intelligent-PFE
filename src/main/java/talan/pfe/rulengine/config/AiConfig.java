package talan.pfe.rulengine.config;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.openai.OpenAiChatModel;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import org.springframework.web.client.RestClient;

@Configuration

public class AiConfig {

    @Bean

    public RestClient.Builder restClientBuilder() {

        HttpComponentsClientHttpRequestFactory factory =

                new HttpComponentsClientHttpRequestFactory();

        factory.setConnectTimeout(10_000);

        factory.setConnectionRequestTimeout(10_000);

        return RestClient.builder().requestFactory(factory);

    }

    // Explicit ChatClient bean so Spring AI doesn't fail silently

    @Bean

    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {

        return ChatClient.builder(openAiChatModel).build();

    }

}
