package com.dsm.dsmlolleague.service

import com.dsm.dsmlolleague.dto.*
import com.dsm.dsmlolleague.entity.*
import com.dsm.dsmlolleague.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val schoolOAuthService: SchoolOAuthService,
    private val riotApiService: RiotApiService,
    private val jwtTokenService: JwtTokenService
) {
    
    @Transactional
    fun loginWithSchoolAccount(loginRequest: SchoolLoginRequest): String {
        // 학교 계정 인증
        val schoolUser = schoolOAuthService.authenticateUser(loginRequest)
            ?: throw RuntimeException("학교 계정 인증에 실패했습니다")
        
        // 기존 사용자 조회 또는 생성
        val user = userRepository.findByAccountId(schoolUser.accountId)
            ?: createUserFromSchoolData(schoolUser)
        
        // JWT 토큰 생성
        return jwtTokenService.generateToken(user)
    }
    
    @Transactional
    fun registerRiotAccount(accountId: String, riotAccountRequest: RiotAccountRequest): UserResponse {
        val user = userRepository.findByAccountId(accountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        // 이미 Riot 계정이 등록된 경우 확인
        if (user.puuid != null) {
            throw RuntimeException("이미 Riot 계정이 등록되어 있습니다")
        }
        
        // Riot ID로 소환사 정보 조회 (새로운 방식)
        val summonerDto = riotApiService.getSummonerByRiotId(
            riotAccountRequest.gameName, 
            riotAccountRequest.tagLine
        ) ?: throw RuntimeException("존재하지 않는 Riot ID입니다 (${riotAccountRequest.gameName}#${riotAccountRequest.tagLine})")
        
        // 중복 PUUID 확인
        userRepository.findByPuuid(summonerDto.puuid)?.let {
            throw RuntimeException("이미 등록된 Riot 계정입니다")
        }
        
        // PUUID 기반으로 리그 정보 조회 (새로운 권장 방식)
        val leagueEntries = riotApiService.getLeagueEntriesByPuuid(summonerDto.puuid)
        val soloRankEntry = leagueEntries.find { it.queueType == "RANKED_SOLO_5x5" }
        
        // 사용자 정보 업데이트
        user.summonerName = "${riotAccountRequest.gameName}#${riotAccountRequest.tagLine}" // Riot ID 형태로 저장
        user.summonerId = summonerDto.id
        user.puuid = summonerDto.puuid
        
        if (soloRankEntry != null) {
            user.tier = Tier.valueOf(soloRankEntry.tier)
            // rank 문자열을 RankLevel enum으로 변환 (I, II, III, IV)
            user.rank = when (soloRankEntry.rank) {
                "I" -> RankLevel.I
                "II" -> RankLevel.II
                "III" -> RankLevel.III
                "IV" -> RankLevel.IV
                else -> null // Master, Grandmaster, Challenger는 rank가 없음
            }
            user.leaguePoints = soloRankEntry.leaguePoints
            user.score = riotApiService.calculateScore(user.tier!!, user.rank, user.leaguePoints)
        }
        
        val savedUser = userRepository.save(user)
        return convertToUserResponse(savedUser)
    }
    
    fun getUserByAccountId(accountId: String): UserResponse {
        val user = userRepository.findByAccountId(accountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        return convertToUserResponse(user)
    }
    
    fun getAllUsers(filter: UserFilterRequest): UserListResponse {
        val users = when {
            filter.tier != null && filter.name != null -> 
                userRepository.findAll().filter { 
                    it.tier == filter.tier && it.name.contains(filter.name, ignoreCase = true)
                }
            filter.tier != null -> userRepository.findByTier(filter.tier)
            filter.name != null -> userRepository.findByNameContainingIgnoreCase(filter.name)
            filter.hasTeam == true -> userRepository.findByTeamIsNotNull()
            filter.hasTeam == false -> userRepository.findByTeamIsNull()
            else -> userRepository.findAllOrderByScoreDesc()
        }
        
        val userResponses = users.map { convertToUserResponse(it) }
        return UserListResponse(userResponses, userResponses.size)
    }
    
    fun getAvailableUsers(tier: Tier?, name: String?): UserListResponse {
        val users = userRepository.findAvailableUsers(tier, name)
        val userResponses = users.map { convertToUserResponse(it) }
        return UserListResponse(userResponses, userResponses.size)
    }
    
    @Transactional
    private fun createUserFromSchoolData(schoolUser: SchoolUserResponse): User {
        val user = User(
            accountId = schoolUser.accountId,
            name = schoolUser.name,
            grade = schoolUser.grade,
            classNum = schoolUser.classNum,
            num = schoolUser.num,
            userRole = UserRole.valueOf(schoolUser.userRole)
        )
        return userRepository.save(user)
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
            score = user.score,
            teamName = user.team?.name,
            isTeamLeader = user.team?.isLeader(user) ?: false
        )
    }
} 