package com.edusync.edusync.backend.registration.repository;

import com.edusync.edusync.backend.registration.entity.RegistrationItem;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationItemRepository extends JpaRepository<RegistrationItem, Long> {

    List<RegistrationItem> findByRequestIdOrderByItemIdAsc(Long requestId);

    List<RegistrationItem> findByRequestIdIn(Collection<Long> requestIds);
}
