package ru.practicum.comments.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.comments.dto.CommentDto;
import ru.practicum.comments.dto.CommentStatusUpdateRequest;
import ru.practicum.comments.dto.NewCommentDto;
import ru.practicum.comments.mapper.CommentMapper;
import ru.practicum.comments.entity.Comment;
import ru.practicum.comments.entity.CommentStatus;
import ru.practicum.comments.repository.CommentRepository;
import ru.practicum.comments.repository.CommentSpecialRepository;
import ru.practicum.events.entity.Event;
import ru.practicum.events.entity.enums.EventState;
import ru.practicum.events.repository.EventRepository;
import ru.practicum.exceptions.IncorrectRequestException;
import ru.practicum.exceptions.ObjectNotFoundException;
import ru.practicum.exceptions.RequestConflictException;
import ru.practicum.users.entity.User;
import ru.practicum.users.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specification.where;
import static ru.practicum.comments.repository.CommentSpecialRepository.*;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final CommentRepository commentRepository;
    private final CommentSpecialRepository commentSpecialRepository;
    private final CommentMapper commentMapper;

    public CommentDto addUserComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        User commenter = userRepository.findById(userId).orElseThrow(() -> {
            throw new ObjectNotFoundException("User with id = " + userId + " was not found.");
        });

        Event event = eventRepository.findById(eventId).orElseThrow(() -> {
            throw new ObjectNotFoundException("Event with id = " + eventId + " doesn't exist.");
        });

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new RequestConflictException("Users are not allowed to comment on unpublished events.");
        }

        Comment comment = commentMapper.newCommentDtoToComment(newCommentDto);
        comment.setUser(commenter);
        comment.setEvent(event);
        comment.setCreatedOn(LocalDateTime.now());
        comment.setStatus(CommentStatus.PENDING);

        comment = commentRepository.save(comment);

        return commentMapper.commentToCommentDto(comment);
    }

    public CommentDto updateUserComment(Long userId, Long eventId, Long commentId, NewCommentDto newCommentDto) {
        Comment comment = commentRepository.findByIdAndUserIdAndEventId(commentId, userId, eventId).orElseThrow(() -> {
            throw new ObjectNotFoundException("Comment with id = " + commentId + " by user id = " + userId +
                    " for event id = " + eventId + " doesn't exist.");
        });

        if (comment.getStatus().equals(CommentStatus.PENDING)) {
            throw new RequestConflictException("Users are not allowed to update comments, which are pending moderation.");
        }

        comment.setText(newCommentDto.getText());
        comment.setCreatedOn(LocalDateTime.now());
        comment.setStatus(CommentStatus.PENDING);

        comment = commentRepository.save(comment);

        return commentMapper.commentToCommentDto(comment);
    }

    public CommentDto getUserEventComment(Long userId, Long eventId, Long commentId) {
        Comment comment = commentRepository.findByIdAndUserIdAndEventId(commentId, userId, eventId).orElseThrow(() -> {
            throw new ObjectNotFoundException("Comment with id = " + commentId + " by user id = " + userId +
                    " for event id = " + eventId + " doesn't exist.");
        });

        if (comment.getStatus().equals(CommentStatus.PENDING)) {
            throw new RequestConflictException("Users are not allowed to review comments, which are pending moderation.");
        }

        return commentMapper.commentToCommentDto(comment);
    }

    public List<CommentDto> getAllUserComments(Long userId) {
        List<Comment> comments = commentRepository.findAllByUserIdAndStatus(userId, CommentStatus.PUBLISHED);

        if (!comments.isEmpty()) {
            return comments.stream()
                    .map(commentMapper::commentToCommentDto)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    public List<CommentDto> getAllUserEventComments(Long userId, Long eventId) {
        List<Comment> comments = commentRepository.findAllByUserIdAndEventIdAndStatus(userId, eventId, CommentStatus.PUBLISHED);

        if (!comments.isEmpty()) {
            return comments.stream()
                    .map(commentMapper::commentToCommentDto)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    @Transactional
    public void deleteUserComment(Long userId, Long eventId, Long commentId) {
        if (commentRepository.existsByIdAndUserIdAndEventIdAndStatus(commentId, userId, eventId, CommentStatus.PUBLISHED)) {
            commentRepository.deleteByIdAndUserIdAndEventIdAndStatus(commentId, userId, eventId, CommentStatus.PUBLISHED);
        } else {
            throw new ObjectNotFoundException("Comment with id = " + commentId + " by user id = " + userId +
                    " for event id = " + eventId + " is pending moderation or doesn't exist.");
        }
    }

    public List<CommentDto> getAdminComments(String text, List<Long> users, List<CommentStatus> statuses, List<Long> events,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                             Integer from, Integer size) {

        if ((rangeStart != null && rangeEnd != null) && (rangeStart.isAfter(rangeEnd) || rangeStart.isEqual(rangeEnd))) {
            throw new IncorrectRequestException("Start time must not after or equal to end time.");
        }

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        Page<Comment> commentsPage = commentSpecialRepository.findAll(where(hasText(text))
                .and(hasUsers(users))
                .and(hasStatuses(statuses))
                .and(hasEvents(events))
                .and(hasRangeStart(rangeStart))
                .and(hasRangeEnd(rangeEnd)), pageable);

        return commentsPage.stream()
                .map(commentMapper::commentToCommentDto)
                .collect(Collectors.toList());
    }

    public List<CommentDto> moderateAdminComments(CommentStatusUpdateRequest updateRequest) {
        List<Comment> comments = commentRepository.findAllByIdInAndStatus(updateRequest.getCommentIds(), CommentStatus.PENDING);

        if (comments.size() != updateRequest.getCommentIds().size()) {
            throw new ObjectNotFoundException("Incorrect comment id(s) in the request body.");
        }

        switch (updateRequest.getStatus()) {
            case PUBLISHED:
                comments.forEach(comment -> comment.setStatus(CommentStatus.PUBLISHED));
                comments = commentRepository.saveAll(comments);
                return comments.stream()
                        .map(commentMapper::commentToCommentDto)
                        .collect(Collectors.toList());
            case DELETED:
                comments.forEach(comment -> commentRepository.deleteAllById(updateRequest.getCommentIds()));
                return new ArrayList<>();
            default:
                throw new IncorrectRequestException("Incorrect admin moderate request with status 'Pending'.");
        }
    }
}