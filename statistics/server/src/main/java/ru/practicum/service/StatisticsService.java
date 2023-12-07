package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.RequestDTO;
import ru.practicum.RequestOutDto;
import ru.practicum.mapper.StatisticsMapper;
import ru.practicum.model.App;
import ru.practicum.model.Request;
import ru.practicum.repository.AppRepository;
import ru.practicum.repository.RequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final RequestRepository requestRepository;
    private final AppRepository appRepository;
    private final StatisticsMapper mapper;

    public void addRequest(RequestDTO requestDto) {
        Optional<App> optionalApp = appRepository.findByName(requestDto.getApp());

        App app = optionalApp.orElseGet(() -> appRepository.save(new App(requestDto.getApp())));

        Request request = mapper.toRequest(requestDto);
        request.setApp(app);
        requestRepository.save(request);
    }

    public List<RequestOutDto> getRequestsWithViews(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {

        if (unique) {
            if (uris == null || uris.isEmpty()) {
                return requestRepository.getUniqueIpRequestsWithoutUri(start, end);
            }
            return requestRepository.getUniqueIpRequestsWithUri(start, end, uris);
        } else {
            if (uris == null || uris.isEmpty()) {
                return requestRepository.getAllRequestsWithoutUri(start, end);
            }
            return requestRepository.getAllRequestsWithUri(start, end, uris);
        }
    }
}