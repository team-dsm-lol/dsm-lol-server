package com.dsm.dsmlolleague.dto

import com.dsm.dsmlolleague.entity.Tier
import com.dsm.dsmlolleague.entity.RankLevel
import com.dsm.dsmlolleague.entity.UserRole
import com.dsm.dsmlolleague.entity.Lane
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
data class SchoolLoginRequest(
    @field:NotBlank(message = "계정 ID는 필수입니다")
    val accountId: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다")
    val password: String
)

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
data class SchoolUserResponse(
    val id: String,
    val accountId: String,
    val password: String,
    val name: String,
    val grade: Int,
    val classNum: Int,
    val num: Int,
    val userRole: String
)

data class RiotAccountRequest(
    @field:NotBlank(message = "게임 이름은 필수입니다")
    val gameName: String,
    
    @field:NotBlank(message = "태그라인은 필수입니다")
    val tagLine: String,
    
    @field:NotNull(message = "모스트 라인은 필수입니다")
    val mostLane: Lane,
    
    @field:NotNull(message = "세컨 라인은 필수입니다")
    val secondLane: Lane
) {
    // 하위 호환성을 위해 summonerName도 지원
    val summonerName: String?
        get() = null
}

data class UserResponse(
    val id: Long,
    val accountId: String,
    val name: String,
    val grade: Int,
    val classNum: Int,
    val num: Int,
    val userRole: UserRole,
    val summonerName: String?,
    val tier: Tier?,
    val rank: RankLevel?,
    val leaguePoints: Int,
    val level: Int,
    val seasonHighestTier: Tier?,
    val seasonHighestRank: RankLevel?,
    val allTimeHighestTier: Tier?,
    val allTimeHighestRank: RankLevel?,
    val masteryBenefit: Int,
    val score: Int,
    val mostLane: Lane?,
    val secondLane: Lane?,
    val teamName: String?,
    val isTeamLeader: Boolean
)

data class UserListResponse(
    val users: List<UserResponse>,
    val totalCount: Int
)

data class UserFilterRequest(
    val tier: Tier? = null,
    val name: String? = null,
    val hasTeam: Boolean? = null
)

data class TopTierUpdateRequest(
    @field:NotNull(message = "모든 시즌 최고 티어는 필수입니다")
    val topTier: Tier,
    
    val topRank: RankLevel? = null
) 