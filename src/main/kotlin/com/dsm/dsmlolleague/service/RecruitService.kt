package com.dsm.dsmlolleague.service

import com.dsm.dsmlolleague.dto.*
import com.dsm.dsmlolleague.entity.*
import com.dsm.dsmlolleague.repository.RecruitRequestRepository
import com.dsm.dsmlolleague.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RecruitService(
    private val recruitRequestRepository: RecruitRequestRepository,
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun sendRecruitRequest(requesterAccountId: String, recruitRequest: RecruitRequestDto): RecruitResponse {
        val requester = userRepository.findByAccountId(requesterAccountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        val team = requester.team ?: throw RuntimeException("소속된 팀이 없습니다")
        
        // 팀장인지 확인
        if (!team.isLeader(requester)) {
            throw RuntimeException("팀장만 영입 요청을 보낼 수 있습니다")
        }
        
        // 팀 인원 수 확인
        if (!team.canRecruit()) {
            throw RuntimeException("팀 인원이 가득 찼습니다")
        }
        
        val targetUser = userRepository.findById(recruitRequest.targetUserId).orElseThrow {
            RuntimeException("대상 사용자를 찾을 수 없습니다")
        }
        
        // 이미 영입 요청이 있는지 확인
        if (recruitRequestRepository.existsByTargetUserAndTeamAndStatus(targetUser, team, RecruitStatus.PENDING)) {
            throw RuntimeException("이미 영입 요청을 보냈습니다")
        }
        
        // 영입 요청 생성
        val request = com.dsm.dsmlolleague.entity.RecruitRequest(
            team = team,
            targetUser = targetUser,
            requester = requester,
            message = recruitRequest.message
        )
        
        val savedRequest = recruitRequestRepository.save(request)
        return convertToRecruitResponse(savedRequest)
    }
    
    @Transactional
    fun respondToRecruitRequest(targetUserAccountId: String, requestId: Long, decision: RecruitDecisionRequest): String {
        val targetUser = userRepository.findByAccountId(targetUserAccountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        val recruitRequest = recruitRequestRepository.findById(requestId).orElseThrow {
            RuntimeException("영입 요청을 찾을 수 없습니다")
        }
        
        // 본인의 영입 요청인지 확인
        if (recruitRequest.targetUser.id != targetUser.id) {
            throw RuntimeException("본인의 영입 요청이 아닙니다")
        }
        
        // 이미 처리된 요청인지 확인
        if (recruitRequest.status != RecruitStatus.PENDING) {
            throw RuntimeException("이미 처리된 영입 요청입니다")
        }
        
        return if (decision.accept) {
            acceptRecruitRequest(recruitRequest, targetUser)
        } else {
            rejectRecruitRequest(recruitRequest)
        }
    }
    
    fun getPendingRequestsForUser(accountId: String): RecruitListResponse {
        val user = userRepository.findByAccountId(accountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        val requests = recruitRequestRepository.findByTargetUserAndStatus(user, RecruitStatus.PENDING)
        val recruitResponses = requests.map { convertToRecruitResponse(it) }
        
        return RecruitListResponse(recruitResponses, recruitResponses.size)
    }
    
    fun getTeamRecruitRequests(leaderAccountId: String): RecruitListResponse {
        val leader = userRepository.findByAccountId(leaderAccountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        val team = leader.team ?: throw RuntimeException("소속된 팀이 없습니다")
        
        if (!team.isLeader(leader)) {
            throw RuntimeException("팀장만 팀의 영입 요청을 조회할 수 있습니다")
        }
        
        val requests = recruitRequestRepository.findByTeamAndStatus(team, RecruitStatus.PENDING)
        val recruitResponses = requests.map { convertToRecruitResponse(it) }
        
        return RecruitListResponse(recruitResponses, recruitResponses.size)
    }
    
    @Transactional
    private fun acceptRecruitRequest(recruitRequest: com.dsm.dsmlolleague.entity.RecruitRequest, targetUser: User): String {
        // 50점 제한 확인
        if (!recruitRequest.team.canRecruitWithNewMember(targetUser.score)) {
            throw RuntimeException("팀 점수 제한을 초과합니다 (최대 50점). 현재 팀 점수: ${recruitRequest.team.totalScore}, 영입 대상자 점수: ${targetUser.score}")
        }
        
        // 기존 팀에서 탈퇴 (있다면)
        targetUser.team?.let { currentTeam ->
            targetUser.team = null
        }
        
        // 새 팀에 합류
        targetUser.team = recruitRequest.team
        userRepository.save(targetUser)
        
        // 영입 요청 상태 변경
        recruitRequest.status = RecruitStatus.ACCEPTED
        recruitRequestRepository.save(recruitRequest)
        
        // 같은 사용자에 대한 다른 PENDING 요청들 모두 REJECTED로 변경
        val otherPendingRequests = recruitRequestRepository.findByTargetUserAndStatus(targetUser, RecruitStatus.PENDING)
        otherPendingRequests.forEach { request ->
            request.status = RecruitStatus.REJECTED
            recruitRequestRepository.save(request)
        }
        
        return "${recruitRequest.team.name} 팀에 합류했습니다"
    }
    
    @Transactional
    private fun rejectRecruitRequest(recruitRequest: com.dsm.dsmlolleague.entity.RecruitRequest): String {
        recruitRequest.status = RecruitStatus.REJECTED
        recruitRequestRepository.save(recruitRequest)
        return "영입 요청을 거절했습니다"
    }
    
    private fun convertToRecruitResponse(recruitRequest: com.dsm.dsmlolleague.entity.RecruitRequest): RecruitResponse {
        return RecruitResponse(
            id = recruitRequest.id!!,
            team = convertToTeamResponse(recruitRequest.team),
            targetUser = convertToUserResponse(recruitRequest.targetUser),
            requester = convertToUserResponse(recruitRequest.requester),
            status = recruitRequest.status,
            message = recruitRequest.message,
            createdAt = recruitRequest.createdAt
        )
    }
    
    private fun convertToTeamResponse(team: Team): TeamResponse {
        val leader = convertToUserResponse(team.leader)
        val members = team.members.map { convertToUserResponse(it) }
        
        return TeamResponse(
            id = team.id!!,
            name = team.name,
            leader = leader,
            members = members,
            totalScore = team.totalScore,
            memberCount = team.memberCount,
            canRecruit = team.canRecruit(),
            createdAt = team.createdAt
        )
    }
    
    private fun convertToUserResponse(user: User): UserResponse {
        return UserResponse(
            id = user.id!!,
            accountId = user.accountId,
            name = user.name,
            grade = user.grade,
            classNum = user.classNum,
            num = user.num,
            userRole = user.userRole,
            summonerName = user.summonerName,
            tier = user.tier,
            rank = user.rank,
            leaguePoints = user.leaguePoints,
            level = user.level ?: 0,
            seasonHighestTier = user.seasonHighestTier,
            seasonHighestRank = user.seasonHighestRank,
            allTimeHighestTier = user.allTimeHighestTier,
            allTimeHighestRank = user.allTimeHighestRank,
            masteryBenefit = user.masteryBenefit,
            score = user.score,
            mostLane = user.mostLane,
            secondLane = user.secondLane,
            teamName = user.team?.name,
            isTeamLeader = user.team?.isLeader(user) ?: false
        )
    }
} 