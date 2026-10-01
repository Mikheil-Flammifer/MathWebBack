package com.mathweb.repository;

import com.mathweb.entity.Video;
import com.mathweb.enums.DifficultyLevel;
import com.mathweb.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    Page<Video> findByStatus(VideoStatus status, Pageable pageable);

    Page<Video> findByDifficultyLevelAndStatus(DifficultyLevel level, VideoStatus status, Pageable pageable);

    List<Video> findByQuestIdAndStatus(Long questId, VideoStatus status);

    @Query("SELECT v FROM Video v WHERE v.status = 'PUBLISHED' AND " +
            "LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Video> searchByTitle(String keyword, Pageable pageable);

    @Modifying
    @Query("UPDATE Video v SET v.viewCount = v.viewCount + 1 WHERE v.id = :id")
    void incrementViewCount(Long id);

    @Query("""
       select v from Video v
       left join v.category c
       left join c.parent p
       where v.status = :status
         and (:categoryId is null or c.id = :categoryId or p.id = :categoryId)
         and (:level is null or v.difficultyLevel = :level)
       """)
    Page<Video> findFiltered(@Param("status") VideoStatus status,
                             @Param("categoryId") Long categoryId,
                             @Param("level") DifficultyLevel level,
                             Pageable pageable);

    @Query("SELECT COUNT(v) FROM Video v WHERE v.uploadedBy.id = :userId")
    long countByUploadedById(Long userId);
}