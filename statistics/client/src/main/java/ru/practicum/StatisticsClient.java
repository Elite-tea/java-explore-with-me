package ru.practicum;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

public class StatisticsClient {
    private final String statsServerUrl;
    private final WebClient webClient;

    public StatisticsClient(@Value("http://localhost:9090") String statsServerUrl) {
        this.statsServerUrl = statsServerUrl;
        webClient = WebClient.create(statsServerUrl);
    }

    public void addRequest(RequestDTO requestDto) {
        webClient.post().uri("/hit").bodyValue(requestDto).retrieve().bodyToMono(Object.class).block();
    }

    public ResponseEntity<List<RequestOutDto>> getStats(String start,
                                                           String end,
                                                           List<String> uris,
                                                           Boolean unique) {

        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/stats")
                            .queryParam("start", start)
                            .queryParam("end", end);
                    if (uris != null)
                        uriBuilder.queryParam("uris", String.join(",", uris));
                    if (unique != null)
                        uriBuilder.queryParam("unique", unique);
                    return uriBuilder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ResponseEntity<List<RequestOutDto>>>() {})
                .block();
    }

    public ResponseEntity<List<RequestOutDto>> getStatsByIp(String start,
                                                               String end,
                                                               List<String> uris,
                                                               Boolean unique,
                                                               String ip) {

        ResponseEntity<List<RequestOutDto>> listResponseEntity = webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/statsByIp")
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("ip", ip);
                    if (uris != null)
                        uriBuilder.queryParam("uris", String.join(",", uris));
                    if (unique != null)
                        uriBuilder.queryParam("unique", unique);
                    return uriBuilder.build();
                })
                .retrieve()
                .toEntityList(RequestOutDto.class)
                .block();
        return listResponseEntity;
    }
}