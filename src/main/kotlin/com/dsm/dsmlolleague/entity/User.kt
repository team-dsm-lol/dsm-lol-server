package com.dsm.dsmlolleague.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, unique = true)
    val accountId: String,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(nullable = false)
    val grade: Int,
    
    @Column(nullable = false)
    val classNum: Int,
    
    @Column(nullable = false)
    val num: Int,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val userRole: UserRole,
    
    @Column(unique = true, nullable = true)
    var summonerName: String? = null,
    
    @Column(nullable = true)
    var summonerId: String? = null,
    
    @Column(nullable = true)
    var puuid: String? = null,
    
    @Enumerated(EnumType.STRING)
    var tier: Tier? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "`rank`")
    var rank: RankLevel? = null,
    
    @Column
    var leaguePoints: Int = 0,
    
    @Column
    var level: Int = 0,
    
    @Enumerated(EnumType.STRING)
    var topTier: Tier? = null,
    
    @Enumerated(EnumType.STRING)
    var topRank: RankLevel? = null,
    
    @Column
    var masteryBenefit: Int = 0,
    
    @Column
    var score: Int = 0,
    
    @ManyToOne
    @JoinColumn(name = "team_id")
    var team: Team? = null,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UserRole {
    SCH, STU, DOR
}

enum class Tier {
    IRON, BRONZE, SILVER, GOLD, PLATINUM, EMERALD, DIAMOND, MASTER, GRANDMASTER, CHALLENGER
}

enum class RankLevel {
    IV, III, II, I
} 