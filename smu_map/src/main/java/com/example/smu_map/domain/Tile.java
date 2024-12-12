package com.example.smu_map.domain;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Data
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드를 포함한 생성자
@Table(name="tiles")
public class Tile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int x;
    private int y;
    private String path;

    public Tile(int x, int y, String path) {
    }
}