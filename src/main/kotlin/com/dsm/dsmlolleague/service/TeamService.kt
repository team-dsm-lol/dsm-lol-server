package com.dsm.dsmlolleague.service

import com.dsm.dsmlolleague.dto.*
import com.dsm.dsmlolleague.entity.*
import com.dsm.dsmlolleague.repository.TeamRepository
import com.dsm.dsmlolleague.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamService(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun createTeam(accountId: String, teamCreateRequest: TeamCreateRequest): TeamResponse {
        val user = userRepository.findByAccountId(accountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        // 이미 팀에 속한 사용자인지 확인
        if (user.team != null) {
            throw RuntimeException("이미 팀에 속해있습니다")
        }
        
        // 이미 팀을 생성한 사용자인지 확인
        teamRepository.findByLeader(user)?.let {
            throw RuntimeException("이미 팀을 생성했습니다")
        }
        
        // 팀 이름 중복 확인
        teamRepository.findByName(teamCreateRequest.name)?.let {
            throw RuntimeException("이미 존재하는 팀 이름입니다")
        }
        
        // 팀 생성
        val team = Team(
            name = teamCreateRequest.name,
            leader = user
        )
        
        val savedTeam = teamRepository.save(team)
        
        // 사용자를 팀에 소속시킴
        user.team = savedTeam
        userRepository.save(user)
        
        return convertToTeamResponse(savedTeam)
    }
    
    fun getAllTeams(): TeamListResponse {
        val teams = teamRepository.findAllOrderByTotalScoreDesc()
        val teamResponses = teams.map { convertToTeamResponse(it) }
        return TeamListResponse(teamResponses, teamResponses.size)
    }
    
    fun getTeamById(teamId: Long): TeamResponse {
        val team = teamRepository.findById(teamId).orElseThrow {
            RuntimeException("팀을 찾을 수 없습니다")
        }
        return convertToTeamResponse(team)
    }
    
    fun getUserTeam(accountId: String): TeamResponse? {
        val user = userRepository.findByAccountId(accountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        return user.team?.let { convertToTeamResponse(it) }
    }
    
    @Transactional
    fun leaveTeam(accountId: String): String {
        val user = userRepository.findByAccountId(accountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        val team = user.team ?: throw RuntimeException("소속된 팀이 없습니다")
        
        // 팀장인 경우 팀 해체
        if (team.isLeader(user)) {
            // 모든 팀원들의 팀 정보 제거
            team.members.forEach { member ->
                member.team = null
                userRepository.save(member)
            }
            teamRepository.delete(team)
            return "팀이 해체되었습니다"
        } else {
            // 일반 팀원인 경우 팀 탈퇴
            user.team = null
            userRepository.save(user)
            return "팀에서 탈퇴했습니다"
        }
    }
    
    @Transactional
    fun kickMember(leaderAccountId: String, targetUserId: Long): String {
        val leader = userRepository.findByAccountId(leaderAccountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        val team = leader.team ?: throw RuntimeException("소속된 팀이 없습니다")
        
        if (!team.isLeader(leader)) {
            throw RuntimeException("팀장만 팀원을 강퇴할 수 있습니다")
        }
        
        val targetUser = userRepository.findById(targetUserId).orElseThrow {
            RuntimeException("대상 사용자를 찾을 수 없습니다")
        }
        
        if (targetUser.team?.id != team.id) {
            throw RuntimeException("해당 사용자는 팀원이 아닙니다")
        }
        
        if (team.isLeader(targetUser)) {
            throw RuntimeException("팀장은 강퇴할 수 없습니다")
        }
        
        targetUser.team = null
        userRepository.save(targetUser)
        
        return "${targetUser.name}님이 팀에서 강퇴되었습니다"
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
            teamName = user.team?.name,
            isTeamLeader = user.team?.isLeader(user) ?: false
        )
    }
} 