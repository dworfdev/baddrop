package com.dworf.slots_demo.service;

import com.dworf.slots_demo.entity.IdempotencyKey;
import com.dworf.slots_demo.repository.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/** H19: must be called inside the money transaction so a failed operation releases its key. */
@Service
public class IdempotencyService {
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(Long userId, String key, String operation) {
        if (key == null || !FORMAT.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required (8-64 chars: A-Z a-z 0-9 _ -)");
        }
        try {
            repository.saveAndFlush(new IdempotencyKey(userId, key, operation));
        } catch (DataIntegrityViolationException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate request (this Idempotency-Key was already used)");
        }
    }
}
