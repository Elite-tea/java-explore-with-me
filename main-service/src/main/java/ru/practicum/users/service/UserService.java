package ru.practicum.users.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.practicum.exceptions.ObjectNotFoundException;
import ru.practicum.exceptions.SQLConstraintViolationException;
import ru.practicum.users.dto.NewUserDto;
import ru.practicum.users.dto.UserDto;
import ru.practicum.users.entity.User;
import ru.practicum.users.mapper.UserMapper;
import ru.practicum.users.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public List<UserDto> getUsers(List<Long> ids, Integer from, Integer size) {
        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        if (ids == null) {
            return userRepository.findAll(pageable).stream()
                    .map(userMapper::userToUserDto)
                    .collect(Collectors.toList());
        } else {
            return userRepository.findAllByIdIn(ids, pageable).stream()
                    .map(userMapper::userToUserDto)
                    .collect(Collectors.toList());
        }
    }

    public UserDto addUser(NewUserDto newUserRequestDto) {
        User user = userMapper.userDtoToUser(newUserRequestDto);

        User userDtoToSave;

        try {
            userDtoToSave = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new SQLConstraintViolationException("User name and/or email already exists.");
        }

        return userMapper.userToUserDto(userDtoToSave);
    }

    public void deleteUserById(Long userId) {
        try {
            userRepository.deleteById(userId);
        } catch (EmptyResultDataAccessException e) {
            throw new ObjectNotFoundException("User with id = " + userId + " was not found.");
        }
    }
}