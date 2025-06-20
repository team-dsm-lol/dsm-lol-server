package com.dsm.dsmlolleague.repository

import com.dsm.dsmlolleague.entity.RecruitRequest
import com.dsm.dsmlolleague.entity.RecruitStatus
import com.dsm.dsmlolleague.entity.Team
import com.dsm.dsmlolleague.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RecruitRequestRepository : JpaRepository<RecruitRequest, Long> {
    
    fun findByTargetUserAndStatus(targetUser: User, status: RecruitStatus): List<RecruitRequest>
    
    fun findByTeamAndStatus(team: Team, status: RecruitStatus): List<RecruitRequest>
    
    fun findByTargetUserAndTeamAndStatus(targetUser: User, team: Team, status: RecruitStatus): RecruitRequest?
    
    fun findByTargetUser(targetUser: User): List<RecruitRequest>
    
    fun existsByTargetUserAndTeamAndStatus(targetUser: User, team: Team, status: RecruitStatus): Boolean
} 