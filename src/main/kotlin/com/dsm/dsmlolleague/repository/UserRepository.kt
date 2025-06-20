package com.dsm.dsmlolleague.repository

import com.dsm.dsmlolleague.entity.User
import com.dsm.dsmlolleague.entity.Tier
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    
    fun findByAccountId(accountId: String): User?
    
    fun findBySummonerName(summonerName: String): User?
    
    fun findByPuuid(puuid: String): User?
    
    fun findByNameContainingIgnoreCase(name: String): List<User>
    
    fun findByTier(tier: Tier): List<User>
    
    fun findByTeamIsNull(): List<User>
    
    fun findByTeamIsNotNull(): List<User>
    
    @Query("SELECT u FROM User u WHERE u.team IS NULL AND (:tier IS NULL OR u.tier = :tier) AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    fun findAvailableUsers(@Param("tier") tier: Tier?, @Param("name") name: String?): List<User>
    
    @Query("SELECT u FROM User u ORDER BY u.score DESC")
    fun findAllOrderByScoreDesc(): List<User>
} 