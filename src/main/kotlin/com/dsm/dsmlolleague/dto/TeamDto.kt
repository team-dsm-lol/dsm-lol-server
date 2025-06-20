package com.dsm.dsmlolleague.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class TeamCreateRequest(
    @field:NotBlank(message = "팀 이름은 필수입니다")
    @field:Size(min = 2, max = 20, message = "팀 이름은 2-20자여야 합니다")
    val name: String
)

data class TeamResponse(
    val id: Long,
    val name: String,
    val leader: UserResponse,
    val members: List<UserResponse>,
    val totalScore: Int,
    val memberCount: Int,
    val canRecruit: Boolean,
    val createdAt: LocalDateTime
)

data class TeamListResponse(
    val teams: List<TeamResponse>,
    val totalCount: Int
)

data class TeamMemberResponse(
    val id: Long,
    val name: String,
    val summonerName: String?,
    val tier: String?,
    val rank: String?,
    val score: Int,
    val isLeader: Boolean
) 