package com.dsm.dsmlolleague.dto

import com.dsm.dsmlolleague.entity.RecruitStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class RecruitRequestDto(
    @field:NotNull(message = "대상 유저 ID는 필수입니다")
    val targetUserId: Long,
    
    @field:Size(max = 200, message = "메시지는 200자 이하여야 합니다")
    val message: String? = null
)

data class RecruitResponse(
    val id: Long,
    val team: TeamResponse,
    val targetUser: UserResponse,
    val requester: UserResponse,
    val status: RecruitStatus,
    val message: String?,
    val createdAt: LocalDateTime
)

data class RecruitDecisionRequest(
    @field:NotNull(message = "승인 여부는 필수입니다")
    val accept: Boolean
)

data class RecruitListResponse(
    val requests: List<RecruitResponse>,
    val totalCount: Int
) 