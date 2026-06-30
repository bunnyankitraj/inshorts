package com.inshorts.news.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Value("${llm.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${llm.http.response-timeout-ms:20000}")
    private int responseTimeoutMs;

    /**
     * WebClient with explicit connect/response timeouts so a slow or unresponsive
     * LLM endpoint fails fast instead of holding a request thread indefinitely.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            // OpenAI responses can be large; lift the default 256KB buffer cap.
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
