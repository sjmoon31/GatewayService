package com.shop.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CustomFilter extends AbstractGatewayFilterFactory<CustomFilter.Config> {
    public CustomFilter(){
        super(Config.class);
    }

    /**
     *
     * @param config
     * @return
     */
    @Override
    public GatewayFilter apply(Config config){
        // Custom Pre Filter
        return ((exchange, chain) -> {
            // ServletHttp 를 비동기 방식에서는 ServerHttp 로 사용한다.
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            log.info("Custom PRE filter: request id -> {}", request.getId());
            // Custom Post Filter.Suppose we can call error response handler based on error code.
            return chain.filter(exchange).then(Mono.fromRunnable(()->{
                log.info("Custom POST filter: response code -> {}", response.getStatusCode());
            }));
        });
    };

    // Mono : 이 객체는 웹 플럭스라고 해서 spring 5에서 추가된 기능, 비동기 방식의 서버 지원 (단일값으로)
    public static class Config{
        // Put the configuration properties
    }

}
