package com.grid07.assignment.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "bots")
@Data
public class Bot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String personaDescription;
}