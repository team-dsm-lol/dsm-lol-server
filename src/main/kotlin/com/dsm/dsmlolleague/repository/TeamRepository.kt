package com.dsm.dsmlolleague.repository

import com.dsm.dsmlolleague.entity.Team
import com.dsm.dsmlolleague.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TeamRepository : JpaRepository<Team, Long> {
    
    fun findByName(name: String): Team?
    
    fun findByLeader(leader: User): Team?
    
    @Query("SELECT t FROM Team t ORDER BY (SELECT SUM(m.score) FROM User m WHERE m.team = t) DESC")
    fun findAllOrderByTotalScoreDesc(): List<Team>
    
    @Query("SELECT t FROM Team t WHERE SIZE(t.members) < 5")
    fun findTeamsWithAvailableSlots(): List<Team>
} 