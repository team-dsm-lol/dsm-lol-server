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
    
    @Enumerated(EnumType.STRING)
    var tier: Tier? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "`rank`")
    var rank: RankLevel? = null,
    
    @Column
    var leaguePoints: Int = 0,
    
    @Column
    var level: Int? = null,
    
    @Enumerated(EnumType.STRING)
    var seasonHighestTier: Tier? = null,
    
    @Enumerated(EnumType.STRING)
    var seasonHighestRank: RankLevel? = null,
    
    @Enumerated(EnumType.STRING)
    var allTimeHighestTier: Tier? = null,
    
    @Enumerated(EnumType.STRING)
    var allTimeHighestRank: RankLevel? = null,
    
    @Column
    var masteryBenefit: Int = 0,
    
    @Column
    var score: Int = 0,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    var mostLane: Lane? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    var secondLane: Lane? = null,
    
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

enum class Lane {
    TOP, JUNGLE, MID, ADC, SUPPORT
}

enum class Tier {
    IRON, BRONZE, SILVER, GOLD, PLATINUM, EMERALD, DIAMOND, MASTER, GRANDMASTER, CHALLENGER
}

enum class RankLevel {
    IV, III, II, I
} 