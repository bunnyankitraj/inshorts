package com.inshorts.news.repository;

import com.inshorts.news.model.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserEventRepository extends JpaRepository<UserEvent, String> {

    @Query("SELECT e FROM UserEvent e WHERE e.eventTime >= :since ORDER BY e.eventTime DESC")
    List<UserEvent> findRecentEvents(@Param("since") LocalDateTime since);
}
