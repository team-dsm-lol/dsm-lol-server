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
        if (user.summonerName != null) {
            throw RuntimeException("이미 Riot 계정이 등록되어 있습니다")
        }
        
        // 실제 소환사 레벨 조회 및 50레벨 이상 체크
        val actualLevel = riotApiService.getSummonerLevel(
            riotAccountRequest.gameName, 
            riotAccountRequest.tagLine
        )
        
        if (actualLevel == null) {
            throw RuntimeException("소환사 정보를 조회할 수 없습니다. 올바른 Riot ID인지 확인해주세요.")
        }
        
        if (actualLevel < 30) {
            throw RuntimeException("30레벨 이상의 계정만 참가 가능합니다. (현재 레벨: ${actualLevel})")
        }
        
        // Rate Limiting을 위한 딜레이 (Riot API 호출 후)
        Thread.sleep(100)
        
        // OP.GG에서 티어 정보 크롤링
        val opggTierInfo = riotApiService.getTierInfoFromOpGG(
            riotAccountRequest.gameName, 
            riotAccountRequest.tagLine
        ) ?: throw RuntimeException("OP.GG에서 해당 소환사를 찾을 수 없습니다 (${riotAccountRequest.gameName}#${riotAccountRequest.tagLine})")
        
        // 중복 등록 확인 (소환사명 기준)
        val summonerName = "${riotAccountRequest.gameName}#${riotAccountRequest.tagLine}"
        userRepository.findBySummonerName(summonerName)?.let {
            throw RuntimeException("이미 등록된 Riot 계정입니다")
        }
        
        // 사용자 정보 업데이트
        user.summonerName = summonerName
        user.level = actualLevel // 실제 조회된 레벨 사용
        user.mostLane = riotAccountRequest.mostLane
        user.secondLane = riotAccountRequest.secondLane
        
        // 이번 시즌 최고 티어 설정
        if (opggTierInfo.currentSeasonHighest != null) {
            user.seasonHighestTier = opggTierInfo.currentSeasonHighest.tier
            user.seasonHighestRank = opggTierInfo.currentSeasonHighest.rank
        }
        
        // 모든 시즌 최고 티어 계산 (전시즌 티어들 + 이번 시즌 최고 티어 중 최고)
        val allTiers = mutableListOf<TierInfo>()
        if (opggTierInfo.currentSeasonHighest != null) {
            allTiers.add(opggTierInfo.currentSeasonHighest)
        }
        allTiers.addAll(opggTierInfo.previousSeasonTiers)
        
        if (allTiers.isNotEmpty()) {
            val allTimeHighest = allTiers.maxByOrNull { 
                riotApiService.calculateTierScore(it.tier, it.rank) 
            }
            if (allTimeHighest != null) {
                user.allTimeHighestTier = allTimeHighest.tier
                user.allTimeHighestRank = allTimeHighest.rank
            }
        }
        
        // 점수 계산에 사용되는 중간 티어를 user.tier와 user.rank에 저장
        val finalTierInfo = riotApiService.calculateFinalTierForScore(
            seasonHighestTier = user.seasonHighestTier,
            seasonHighestRank = user.seasonHighestRank,
            allTimeHighestTier = user.allTimeHighestTier,
            allTimeHighestRank = user.allTimeHighestRank
        )
        user.tier = finalTierInfo.tier
        user.rank = finalTierInfo.rank
        
//        // 장인 베네핏 계산 (Riot API 기반)
//        user.masteryBenefit = riotApiService.calculateMasteryBenefitFromRiotAPI(
//            riotAccountRequest.gameName,
//            riotAccountRequest.tagLine
//        )
        
        // 새로운 점수 계산 방식 적용
        user.score = riotApiService.calculateScore(
            seasonHighestTier = user.seasonHighestTier,
            seasonHighestRank = user.seasonHighestRank,
            allTimeHighestTier = user.allTimeHighestTier,
            allTimeHighestRank = user.allTimeHighestRank,
            level = user.level,
            masteryBenefit = user.masteryBenefit
        )
        
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
    
    @Transactional
    fun updateTopTierInfo(accountId: String, topTier: Tier, topRank: RankLevel?): UserResponse {
        val user = userRepository.findByAccountId(accountId)
            ?: throw RuntimeException("사용자를 찾을 수 없습니다")
        
        user.allTimeHighestTier = topTier
        user.allTimeHighestRank = topRank
        
        // 점수 계산에 사용되는 중간 티어를 user.tier와 user.rank에 저장
        val finalTierInfo = riotApiService.calculateFinalTierForScore(
            seasonHighestTier = user.seasonHighestTier,
            seasonHighestRank = user.seasonHighestRank,
            allTimeHighestTier = user.allTimeHighestTier,
            allTimeHighestRank = user.allTimeHighestRank
        )
        user.tier = finalTierInfo.tier
        user.rank = finalTierInfo.rank
        
        // 점수 재계산
        user.score = riotApiService.calculateScore(
            seasonHighestTier = user.seasonHighestTier,
            seasonHighestRank = user.seasonHighestRank,
            allTimeHighestTier = user.allTimeHighestTier,
            allTimeHighestRank = user.allTimeHighestRank,
            level = user.level,
            masteryBenefit = user.masteryBenefit
        )
        
        val savedUser = userRepository.save(user)
        return convertToUserResponse(savedUser)
    }
    
    @Transactional
    fun recalculateAllScores(): String {
        val users = userRepository.findAll().filter { it.summonerName != null }
        var updatedCount = 0
        
        users.forEach { user ->
            try {
                // 소환사명에서 게임명과 태그라인 분리
                val nameParts = user.summonerName!!.split("#")
                if (nameParts.size != 2) {
                    println("잘못된 소환사명 형식: ${user.summonerName}")
                    return@forEach
                }
                
                val gameName = nameParts[0]
                val tagLine = nameParts[1]
                
                println("${user.name} OP.GG 점수 재계산 시작...")
                
                // OP.GG에서 최신 티어 정보 크롤링
                val opggTierInfo = riotApiService.getTierInfoFromOpGG(gameName, tagLine)
                
                if (opggTierInfo != null) {
                    // 이번 시즌 최고 티어 업데이트
                    if (opggTierInfo.currentSeasonHighest != null) {
                        user.seasonHighestTier = opggTierInfo.currentSeasonHighest.tier
                        user.seasonHighestRank = opggTierInfo.currentSeasonHighest.rank
                    }
                    
                    // 모든 시즌 최고 티어 계산
                    val allTiers = mutableListOf<TierInfo>()
                    if (opggTierInfo.currentSeasonHighest != null) {
                        allTiers.add(opggTierInfo.currentSeasonHighest)
                    }
                    allTiers.addAll(opggTierInfo.previousSeasonTiers)
                    
                    if (allTiers.isNotEmpty()) {
                        val allTimeHighest = allTiers.maxByOrNull { 
                            riotApiService.calculateTierScore(it.tier, it.rank) 
                        }
                        if (allTimeHighest != null) {
                            user.allTimeHighestTier = allTimeHighest.tier
                            user.allTimeHighestRank = allTimeHighest.rank
                        }
                    }
                    
                    // 점수 계산에 사용되는 중간 티어를 user.tier와 user.rank에 저장
                    val finalTierInfo = riotApiService.calculateFinalTierForScore(
                        seasonHighestTier = user.seasonHighestTier,
                        seasonHighestRank = user.seasonHighestRank,
                        allTimeHighestTier = user.allTimeHighestTier,
                        allTimeHighestRank = user.allTimeHighestRank
                    )
                    user.tier = finalTierInfo.tier
                    user.rank = finalTierInfo.rank
                    
                    // 소환사 레벨 재조회 및 업데이트
                    val actualLevel = riotApiService.getSummonerLevel(gameName, tagLine)
                    if (actualLevel != null) {
                        user.level = actualLevel
                        println("${user.name} 레벨 업데이트: ${actualLevel}")
                    }
                    
                    // Rate Limiting을 위한 딜레이 (레벨 조회 후)
                    Thread.sleep(100)
                    
                    // 장인 베네핏 재계산
                    user.masteryBenefit = riotApiService.calculateMasteryBenefitFromRiotAPI(gameName, tagLine)
                    
                    // 점수 재계산
                    user.score = riotApiService.calculateScore(
                        seasonHighestTier = user.seasonHighestTier,
                        seasonHighestRank = user.seasonHighestRank,
                        allTimeHighestTier = user.allTimeHighestTier,
                        allTimeHighestRank = user.allTimeHighestRank,
                        level = user.level,
                        masteryBenefit = user.masteryBenefit
                    )
                    
                    userRepository.save(user)
                    updatedCount++
                    
                    println("${user.name} 점수 재계산 완료")
                }
                
                // Rate Limiting
                Thread.sleep(2000) // 2초 딜레이
                
            } catch (e: Exception) {
                println("사용자 ${user.name} 점수 재계산 실패: ${e.message}")
            }
        }
        
        return "총 ${users.size}명 중 ${updatedCount}명의 점수가 업데이트되었습니다."
    }
    
    @Transactional
    fun analyzeAllUsersSeasonTiers(): String {
        val users = userRepository.findAll().filter { it.summonerName != null }
        var analyzedCount = 0
        var improvedCount = 0
        
        users.forEach { user ->
            try {
                // 소환사명에서 게임명과 태그라인 분리
                val nameParts = user.summonerName!!.split("#")
                if (nameParts.size != 2) {
                    println("잘못된 소환사명 형식: ${user.summonerName}")
                    return@forEach
                }
                
                val gameName = nameParts[0]
                val tagLine = nameParts[1]
                
                println("${user.name} OP.GG 시즌별 티어 분석 시작...")
                
                // OP.GG에서 티어 정보 크롤링
                val opggTierInfo = riotApiService.getTierInfoFromOpGG(gameName, tagLine)
                
                if (opggTierInfo != null) {
                    // 모든 시즌 최고 티어 재계산
                    val allTiers = mutableListOf<TierInfo>()
                    if (opggTierInfo.currentSeasonHighest != null) {
                        allTiers.add(opggTierInfo.currentSeasonHighest)
                    }
                    allTiers.addAll(opggTierInfo.previousSeasonTiers)
                    
                    if (allTiers.isNotEmpty()) {
                        val newAllTimeHighest = allTiers.maxByOrNull { 
                            riotApiService.calculateTierScore(it.tier, it.rank) 
                        }
                        
                        if (newAllTimeHighest != null) {
                            val currentAllTimeScore = riotApiService.calculateTierScore(
                                user.allTimeHighestTier, user.allTimeHighestRank
                            )
                            val newAllTimeScore = riotApiService.calculateTierScore(
                                newAllTimeHighest.tier, newAllTimeHighest.rank
                            )
                            
                            // 분석된 티어가 더 높다면 업데이트
                            if (newAllTimeScore > currentAllTimeScore) {
                                user.allTimeHighestTier = newAllTimeHighest.tier
                                user.allTimeHighestRank = newAllTimeHighest.rank
                                
                                // 점수 계산에 사용되는 중간 티어를 user.tier와 user.rank에 저장
                                val finalTierInfo = riotApiService.calculateFinalTierForScore(
                                    seasonHighestTier = user.seasonHighestTier,
                                    seasonHighestRank = user.seasonHighestRank,
                                    allTimeHighestTier = user.allTimeHighestTier,
                                    allTimeHighestRank = user.allTimeHighestRank
                                )
                                user.tier = finalTierInfo.tier
                                user.rank = finalTierInfo.rank
                                
                                // 소환사 레벨도 함께 업데이트
                                val actualLevel = riotApiService.getSummonerLevel(gameName, tagLine)
                                if (actualLevel != null) {
                                    user.level = actualLevel
                                    println("${user.name} 레벨 업데이트: ${actualLevel}")
                                }
                                
                                // Rate Limiting을 위한 딜레이 (레벨 조회 후)
                                Thread.sleep(100)
                                
                                // 점수 재계산
                                user.score = riotApiService.calculateScore(
                                    seasonHighestTier = user.seasonHighestTier,
                                    seasonHighestRank = user.seasonHighestRank,
                                    allTimeHighestTier = user.allTimeHighestTier,
                                    allTimeHighestRank = user.allTimeHighestRank,
                                    level = user.level,
                                    masteryBenefit = user.masteryBenefit
                                )
                                
                                userRepository.save(user)
                                improvedCount++
                                
                                println("${user.name} - 모든 시즌 최고 티어 업데이트: ${newAllTimeHighest.tier} ${newAllTimeHighest.rank ?: ""}")
                            }
                            
                            // 분석 결과 출력
                            println("${user.name} OP.GG 분석 결과:")
                            println("  이번 시즌 최고: ${opggTierInfo.currentSeasonHighest?.tier} ${opggTierInfo.currentSeasonHighest?.rank ?: ""}")
                            opggTierInfo.previousSeasonTiers.forEachIndexed { index, tierInfo ->
                                println("  전시즌 ${index + 1}: ${tierInfo.tier} ${tierInfo.rank ?: ""}")
                            }
                        }
                    }
                }
                
                analyzedCount++
                
                // Rate Limiting을 위한 딜레이
                Thread.sleep(3000) // 3초 딜레이
                
            } catch (e: Exception) {
                println("${user.name} OP.GG 시즌별 티어 분석 실패: ${e.message}")
            }
        }
        
        return "총 ${users.size}명 중 ${analyzedCount}명 분석 완료, ${improvedCount}명의 모든 시즌 최고 티어 개선됨 (OP.GG 기반)"
    }
    
    @Transactional
    fun updateAllUsersLevel(): String {
        val users = userRepository.findAll().filter { it.summonerName != null }
        var updatedCount = 0
        
        users.forEach { user ->
            try {
                // 소환사명에서 게임명과 태그라인 분리
                val nameParts = user.summonerName!!.split("#")
                if (nameParts.size != 2) {
                    println("잘못된 소환사명 형식: ${user.summonerName}")
                    return@forEach
                }
                
                val gameName = nameParts[0]
                val tagLine = nameParts[1]
                
                println("${user.name} 레벨 업데이트 시작... (현재 레벨: ${user.level})")
                
                // 실제 소환사 레벨 조회
                val actualLevel = riotApiService.getSummonerLevel(gameName, tagLine)
                if (actualLevel != null) {
                    val oldLevel = user.level
                    user.level = actualLevel
                    
                    // 점수 계산에 사용되는 중간 티어를 user.tier와 user.rank에 저장
                    val finalTierInfo = riotApiService.calculateFinalTierForScore(
                        seasonHighestTier = user.seasonHighestTier,
                        seasonHighestRank = user.seasonHighestRank,
                        allTimeHighestTier = user.allTimeHighestTier,
                        allTimeHighestRank = user.allTimeHighestRank
                    )
                    user.tier = finalTierInfo.tier
                    user.rank = finalTierInfo.rank
                    
                    // 점수 재계산 (레벨별 최소 점수 적용)
                    user.score = riotApiService.calculateScore(
                        seasonHighestTier = user.seasonHighestTier,
                        seasonHighestRank = user.seasonHighestRank,
                        allTimeHighestTier = user.allTimeHighestTier,
                        allTimeHighestRank = user.allTimeHighestRank,
                        level = user.level,
                        masteryBenefit = user.masteryBenefit
                    )
                    
                    userRepository.save(user)
                    updatedCount++
                    
                    println("${user.name} 레벨 업데이트 완료: ${oldLevel} → ${actualLevel} (점수: ${user.score})")
                } else {
                    println("${user.name} 레벨 조회 실패")
                }
                
                // Rate Limiting
                Thread.sleep(150) // 150ms 딜레이
                
            } catch (e: Exception) {
                println("사용자 ${user.name} 레벨 업데이트 실패: ${e.message}")
            }
        }
        
        return "총 ${users.size}명 중 ${updatedCount}명의 레벨이 업데이트되었습니다."
    }
} 