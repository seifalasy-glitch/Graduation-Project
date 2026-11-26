package com.edusync.edusync.backend.registration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "registration_item")
@Getter
@Setter
public class RegistrationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "offering_id", nullable = false)
    private Long offeringId;

    @Column(name = "status", nullable = false)
    private String status;
}
