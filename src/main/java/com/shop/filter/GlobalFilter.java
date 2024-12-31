package com.shop.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class GlobalFilter extends AbstractGatewayFilterFactory<GlobalFilter.Config> {

    private final WebClient webClient;

    @Value("${auth.service.url}")
    private String authServiceUrl; // AuthService의 URL

    private final List<String> excludedPaths = List.of(
            "/member", "/product/main"
    );

    public GlobalFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            log.info("Global Filter baseMessage: {}", config.getBaseMessage());

            // 인증이 필요 없는 경로인지 확인
            String requestPath = request.getURI().getPath();
            if (excludedPaths.stream().anyMatch(requestPath::startsWith)) {
                log.info("Skipping authentication for path: {}", requestPath);
                return chain.filter(exchange);
            }

            // Pre Filter: JWT 검증
            if (config.isPreLogger()) {
                log.info("Global Filter Start: request id -> {}", request.getId());
            }

            String jwtToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (jwtToken == null || !jwtToken.startsWith("Bearer ")) {
                log.error("Missing or invalid Authorization header");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }

            // AuthService 호출
            return webClient
                    .method(HttpMethod.GET)
                    .uri(authServiceUrl + "/auth")
                    .header(HttpHeaders.AUTHORIZATION, jwtToken)
                    .retrieve()
                    .bodyToMono(String.class) // 이메일 반환
                    .flatMap(email -> {
                        // 요청 헤더에 이메일 추가
                        ServerHttpRequest modifiedRequest = request.mutate()
                                .header("email", email)
                                .build();
                        return chain.filter(exchange.mutate().request(modifiedRequest).build());
                    })
                    .onErrorResume(error -> {
                        log.error("JWT validation failed: {}", error.getMessage());
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return response.setComplete();
                    })
                    .then(Mono.fromRunnable(() -> {
                        // Post Filter
                        if (config.isPostLogger()) {
                            log.info("Global Filter End: response code -> {}", response.getStatusCode());
                        }
                    }));
        });
    }

    @Data
    public static class Config {
        private String baseMessage;
        private boolean preLogger;
        private boolean postLogger;
    }
}
