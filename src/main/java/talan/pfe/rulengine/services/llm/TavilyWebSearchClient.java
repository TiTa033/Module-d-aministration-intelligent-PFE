package talan.pfe.rulengine.services.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TavilyWebSearchClient {

    @Value("${tavily.api-key}")
    private String apiKey;

    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private final RestTemplate restTemplate = new RestTemplate();

    public SearchResponse search(String query, int maxResults) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "query", query,
                    "search_depth", "advanced",
                    "topic", "finance",
                    "max_results", maxResults,
                    "include_answer", true
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<SearchResponse> response = restTemplate.exchange(
                    TAVILY_URL, HttpMethod.POST, request, SearchResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Tavily search failed for query='{}': {}", query, e.getMessage());
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResponse {
        private String query;
        private String answer;
        private List<ResultItem> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultItem {
        private String title;
        private String url;
        private String content;
        private Double score;
    }
}