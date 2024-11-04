package com.api.javawebapi.repository;

import com.api.javawebapi.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
}
