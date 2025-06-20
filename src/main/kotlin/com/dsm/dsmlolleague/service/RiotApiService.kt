package com.dsm.dsmlolleague.service

import com.dsm.dsmlolleague.entity.Tier
import com.dsm.dsmlolleague.entity.RankLevel
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class RiotApiService(
    @Value("\${riot.api.key}")
    private val apiKey: String,
    
    @Value("\${riot.api.base-url}")
    private val baseUrl: String
) {
    
    private val httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 연결 타임아웃 30초
        .doOnConnected { conn ->
            conn.addHandlerLast(ReadTimeoutHandler(60, TimeUnit.SECONDS)) // 읽기 타임아웃 60초
                .addHandlerLast(WriteTimeoutHandler(60, TimeUnit.SECONDS)) // 쓰기 타임아웃 60초
        }
    
    private val webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("X-Riot-Token", apiKey)
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .build()
    
    private val accountWebClient = WebClient.builder()
        .baseUrl("https://asia.api.riotgames.com") // 아시아 지역 (한국 포함)
        .defaultHeader("X-Riot-Token", apiKey)
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .build()
    
    /**
     * Riot ID (gameName#tagLine)로 소환사 정보를 조회합니다.
     * 새로운 권장 방식입니다.
     */
    fun getSummonerByRiotId(gameName: String, tagLine: String): SummonerDto? {
        return try {
            // 1. Riot ID로 PUUID 조회
            val accountDto = accountWebClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .bodyToMono(AccountDto::class.java)
                .block() ?: return null
            
            // 2. PUUID로 소환사 정보 조회
            getSummonerByPuuid(accountDto.puuid)
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                null
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 소환사 이름으로 조회 (deprecated 방식, 하위 호환성을 위해 유지)
     * 새로운 소환사들은 무작위 UUID가 할당되므로 사용 권장하지 않음
     */
    @Deprecated("Use getSummonerByRiotId instead")
    fun getSummonerByName(summonerName: String): SummonerDto? {
        return try {
            webClient.get()
                .uri("/lol/summoner/v4/summoners/by-name/{summonerName}", summonerName)
                .retrieve()
                .bodyToMono(SummonerDto::class.java)
                .block()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                null
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * PUUID로 소환사 정보를 조회합니다.
     */
    fun getSummonerByPuuid(puuid: String): SummonerDto? {
        return try {
            webClient.get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .bodyToMono(SummonerDto::class.java)
                .block()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                null
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * PUUID로 리그 엔트리를 조회합니다. (권장 방식)
     */
    fun getLeagueEntriesByPuuid(puuid: String): List<LeagueEntryDto> {
        return try {
            webClient.get()
                .uri("/lol/league/v4/entries/by-puuid/{puuid}", puuid)
                .retrieve()
                .bodyToFlux(LeagueEntryDto::class.java)
                .collectList()
                .block() ?: emptyList()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                emptyList()
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 소환사 ID로 리그 엔트리를 조회합니다. (하위 호환성)
     */
    fun getLeagueEntries(summonerId: String): List<LeagueEntryDto> {
        return try {
            webClient.get()
                .uri("/lol/league/v4/entries/by-summoner/{summonerId}", summonerId)
                .retrieve()
                .bodyToFlux(LeagueEntryDto::class.java)
                .collectList()
                .block() ?: emptyList()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                emptyList()
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * PUUID로 챔피언 숙련도 정보를 조회합니다.
     */
    fun getChampionMasteries(puuid: String): List<ChampionMasteryDto> {
        return try {
            webClient.get()
                .uri("/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}", puuid)
                .retrieve()
                .bodyToFlux(ChampionMasteryDto::class.java)
                .collectList()
                .block() ?: emptyList()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                emptyList()
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * PUUID로 최근 매치 ID 목록을 조회합니다.
     */
    fun getRecentMatchIds(puuid: String, count: Int = 100): List<String> {
        return try {
            accountWebClient.get()
                .uri("/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}&queue=420", puuid, count) // 420 = 솔로랭크
                .retrieve()
                .bodyToMono(Array<String>::class.java)
                .map { it.toList() }
                .block() ?: emptyList()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                emptyList()
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 매치 ID로 매치 세부 정보를 조회합니다.
     */
    fun getMatchDetails(matchId: String): MatchDto? {
        return try {
            accountWebClient.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .bodyToMono(MatchDto::class.java)
                .block()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                null
            } else {
                throw RuntimeException("Riot API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 현재 시즌에서 가장 많이 플레이한 챔피언 기반으로 장인 베네핏을 계산합니다.
     * 
     * 장인 기준:
     * - 모스트 2개 80%: -1점
     * - 모스트 2개 90%: -2점  
     * - 모스트 1개 80%: -3점
     * - 모스트 1개 90%: -4점
     */
    fun calculateMasteryBenefitFromMatches(puuid: String): Int {
        try {
            // 최근 100게임 조회
            val matchIds = getRecentMatchIds(puuid, 100)
            if (matchIds.isEmpty()) return 0
            
            val championPlayCounts = mutableMapOf<Int, Int>()
            var totalGames = 0
            var requestCount = 0
            
            // 각 매치에서 플레이한 챔피언 카운트
            for (matchId in matchIds) {
                // Rate Limiting: 1초에 15개 요청 제한 (67ms마다 요청)
                if (requestCount > 0) {
                    Thread.sleep(70) // 70ms 딜레이로 안전하게 처리
                }
                
                val matchDetails = getMatchDetails(matchId) ?: continue
                requestCount++
                
                // 현재 시즌 매치인지 확인 (2024년 1월 이후로 가정)
                val gameCreation = matchDetails.info.gameCreation
                val currentSeasonStart = 1704067200000L // 2024-01-01 00:00:00 UTC
                if (gameCreation < currentSeasonStart) continue
                
                // 해당 플레이어의 참가자 정보 찾기
                val participant = matchDetails.info.participants.find { it.puuid == puuid }
                if (participant != null) {
                    championPlayCounts[participant.championId] = 
                        championPlayCounts.getOrDefault(participant.championId, 0) + 1
                    totalGames++
                }
                
                // 너무 많은 요청을 방지하기 위해 50게임만 분석
                if (totalGames >= 50) break
            }
            
            if (totalGames == 0) return 0
            
            // 가장 많이 플레이한 챔피언들 정렬
            val sortedChampions = championPlayCounts.toList()
                .sortedByDescending { it.second }
                .take(2)
            
            if (sortedChampions.isEmpty()) return 0
            
            // 플레이 비율 계산
            val firstChampionRate = sortedChampions[0].second.toDouble() / totalGames
            val secondChampionRate = if (sortedChampions.size > 1) {
                sortedChampions[1].second.toDouble() / totalGames
            } else 0.0
            
            println("장인 베네핏 계산 완료 - 총 ${totalGames}게임 분석, 1위: ${(firstChampionRate * 100).toInt()}%, 2위: ${(secondChampionRate * 100).toInt()}%")
            
            return when {
                // 모스트 1개 90% 이상 (최고 베네핏)
                firstChampionRate >= 0.9 -> -4
                
                // 모스트 1개 80% 이상
                firstChampionRate >= 0.8 -> -3
                
                // 모스트 2개 90% 이상 (각각 45% 이상이면 합쳐서 90%)
                sortedChampions.size >= 2 && 
                firstChampionRate >= 0.45 && secondChampionRate >= 0.45 -> -2
                
                // 모스트 2개 80% 이상 (각각 40% 이상이면 합쳐서 80%)
                sortedChampions.size >= 2 && 
                firstChampionRate >= 0.4 && secondChampionRate >= 0.4 -> -1
                
                else -> 0
            }
            
        } catch (e: Exception) {
            println("장인 베네핏 계산 실패: ${e.message}")
            return 0
        }
    }
    
    /**
     * 챔피언 숙련도 기반으로 장인 베네핏을 계산합니다.
     * @deprecated 현재 시즌 플레이 기록 기반으로 변경됨
     */
    @Deprecated("Use calculateMasteryBenefitFromMatches instead")
    fun calculateMasteryBenefit(masteries: List<ChampionMasteryDto>): Int {
        if (masteries.isEmpty()) return 0
        
        // 숙련도 레벨 7인 챔피언들만 필터링 (최고 숙련도)
        val masterLevelChampions = masteries.filter { it.championLevel == 7 }
        if (masterLevelChampions.isEmpty()) return 0
        
        // 챔피언 포인트 기준으로 정렬 (상위 2개가 모스트)
        val topMasteries = masterLevelChampions.sortedByDescending { it.championPoints }.take(2)
        
        // 각 챔피언의 숙련도 퍼센테이지 계산 (간단화: 포인트 기준)
        // 실제로는 더 복잡한 계산이 필요할 수 있지만, 일단 포인트 기준으로 함
        val highMasteryChampions = topMasteries.filter { it.championPoints >= 100000 } // 10만 포인트 이상을 고숙련도로 가정
        val veryHighMasteryChampions = topMasteries.filter { it.championPoints >= 200000 } // 20만 포인트 이상을 매우 고숙련도로 가정
        
        return when {
            // 모스트 2개 90% (20만 포인트 이상)
            veryHighMasteryChampions.size >= 2 -> -4
            // 모스트 1개 90% (20만 포인트 이상)  
            veryHighMasteryChampions.size >= 1 -> -3
            // 모스트 2개 80% (10만 포인트 이상)
            highMasteryChampions.size >= 2 -> -1
            // 모스트 1개 80% (10만 포인트 이상)
            highMasteryChampions.size >= 1 -> -2
            else -> 0
        }
    }
    
    fun calculateScore(
        currentTier: Tier?, 
        currentRank: RankLevel?, 
        topTier: Tier?, 
        topRank: RankLevel?, 
        level: Int, 
        masteryBenefit: Int = 0
    ): Int {
        // 현재 시즌 점수 계산
        val currentScore = calculateTierScore(currentTier, currentRank)
        
        // 탑레(전 시즌 최고 티어) 점수 계산
        val topScore = calculateTierScore(topTier, topRank)
        
        // 현재 시즌이 언랭인 경우 탑레 기준으로만 계산
        val baseScore = if (currentTier == null) {
            topScore
        } else {
            // 현재 시즌과 탑레의 중간값 계산
            (currentScore + topScore) / 2
        }
        
        // 레벨별 최소 점수 적용
        val levelMinScore = when {
            level >= 300 -> 13
            level >= 200 -> 9
            level >= 100 -> 6
            else -> 0
        }
        
        // 최종 점수 = max(기본 점수, 레벨 최소 점수) + 장인 베네핏
        return maxOf(baseScore, levelMinScore) + masteryBenefit
    }
    
    private fun calculateTierScore(tier: Tier?, rank: RankLevel?): Int {
        return when (tier) {
            Tier.EMERALD -> when (rank) {
                RankLevel.I, RankLevel.II -> 22
                RankLevel.III, RankLevel.IV -> 20
                null -> 22 // null인 경우 높은 쪽으로 계산
            }
            Tier.PLATINUM -> when (rank) {
                RankLevel.I, RankLevel.II -> 18
                RankLevel.III, RankLevel.IV -> 16
                null -> 18
            }
            Tier.GOLD -> when (rank) {
                RankLevel.I, RankLevel.II -> 14
                RankLevel.III, RankLevel.IV -> 12
                null -> 14
            }
            Tier.SILVER -> when (rank) {
                RankLevel.I, RankLevel.II -> 10
                RankLevel.III, RankLevel.IV -> 8
                null -> 10
            }
            Tier.BRONZE -> when (rank) {
                RankLevel.I, RankLevel.II -> 7
                RankLevel.III, RankLevel.IV -> 5
                null -> 7
            }
            Tier.IRON, null -> 3 // 아이언 또는 언랭
            Tier.DIAMOND -> 25 // 다이아몬드 이상은 별도 처리 (참가 제한)
            Tier.MASTER -> 30
            Tier.GRANDMASTER -> 35
            Tier.CHALLENGER -> 40
        }
    }
}

data class AccountDto(
    val puuid: String,
    val gameName: String,
    val tagLine: String
)

data class SummonerDto(
    val id: String,
    val accountId: String,
    val puuid: String,
    val revisionDate: Long,
    val summonerLevel: Long
)

data class LeagueEntryDto(
    val leagueId: String,
    val summonerId: String,
    val puuid: String,
    val queueType: String,
    val tier: String,
    val rank: String,
    val leaguePoints: Int,
    val wins: Int,
    val losses: Int,
    val hotStreak: Boolean,
    val veteran: Boolean,
    val freshBlood: Boolean,
    val inactive: Boolean,
    val miniSeries: MiniSeriesDto?
)

data class MiniSeriesDto(
    val target: Int,
    val wins: Int,
    val losses: Int,
    val progress: String
)

data class ChampionMasteryDto(
    val puuid: String,
    val championId: Long,
    val championLevel: Int,
    val championPoints: Int,
    val lastPlayTime: Long,
    val championPointsSinceLastLevel: Int,
    val championPointsUntilNextLevel: Int,
    val chestGranted: Boolean,
    val tokensEarned: Int
)

data class MatchDto(
    val metadata: MatchMetadataDto,
    val info: MatchInfoDto
)

data class MatchMetadataDto(
    val dataVersion: String,
    val matchId: String,
    val participants: List<String>
)

data class MatchInfoDto(
    val gameCreation: Long,
    val gameDuration: Long,
    val gameId: Long,
    val gameMode: String,
    val gameName: String,
    val gameStartTimestamp: Long,
    val gameType: String,
    val gameVersion: String,
    val mapId: Int,
    val participants: List<ParticipantDto>,
    val platformId: String,
    val queueId: Int,
    val teams: List<TeamDto>,
    val tournamentCode: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParticipantDto(
    val assists: Int,
    val baronKills: Int,
    val bountyLevel: Int,
    val champExperience: Int,
    val champLevel: Int,
    val championId: Int,
    val championName: String?,
    val championTransform: Int,
    val consumablesPurchased: Int,
    val damageDealtToBuildings: Int,
    val damageDealtToObjectives: Int,
    val damageDealtToTurrets: Int,
    val damageSelfMitigated: Int,
    val deaths: Int,
    val detectorWardsPlaced: Int,
    val doubleKills: Int,
    val dragonKills: Int,
    val firstBloodAssist: Boolean,
    val firstBloodKill: Boolean,
    val firstTowerAssist: Boolean,
    val firstTowerKill: Boolean,
    val gameEndedInEarlySurrender: Boolean,
    val gameEndedInSurrender: Boolean,
    val goldEarned: Int,
    val goldSpent: Int,
    val individualPosition: String?,
    val inhibitorKills: Int,
    val inhibitorTakedowns: Int,
    val inhibitorsLost: Int,
    val item0: Int,
    val item1: Int,
    val item2: Int,
    val item3: Int,
    val item4: Int,
    val item5: Int,
    val item6: Int,
    val itemsPurchased: Int,
    val killingSprees: Int,
    val kills: Int,
    val lane: String?,
    val largestCriticalStrike: Int,
    val largestKillingSpree: Int,
    val largestMultiKill: Int,
    val longestTimeSpentLiving: Int,
    val magicDamageDealt: Int,
    val magicDamageDealtToChampions: Int,
    val magicDamageTaken: Int,
    val neutralMinionsKilled: Int,
    val nexusKills: Int,
    val nexusLost: Int,
    val nexusTakedowns: Int,
    val objectivesStolen: Int,
    val objectivesStolenAssists: Int,
    val participantId: Int,
    val pentaKills: Int,
    val physicalDamageDealt: Int,
    val physicalDamageDealtToChampions: Int,
    val physicalDamageTaken: Int,
    val profileIcon: Int,
    val puuid: String,
    val quadraKills: Int,
    val riotIdName: String?,
    val riotIdTagline: String?,
    val role: String?,
    val sightWardsBoughtInGame: Int,
    val spell1Casts: Int,
    val spell2Casts: Int,
    val spell3Casts: Int,
    val spell4Casts: Int,
    val summoner1Casts: Int,
    val summoner1Id: Int,
    val summoner2Casts: Int,
    val summoner2Id: Int,
    val summonerId: String?,
    val summonerLevel: Int,
    val summonerName: String?,
    val teamEarlySurrendered: Boolean,
    val teamId: Int,
    val teamPosition: String?,
    val timeCCingOthers: Int,
    val timePlayed: Int,
    val totalDamageDealt: Int,
    val totalDamageDealtToChampions: Int,
    val totalDamageShieldedOnTeammates: Int,
    val totalDamageTaken: Int,
    val totalHeal: Int,
    val totalHealsOnTeammates: Int,
    val totalMinionsKilled: Int,
    val totalTimeCCDealt: Int,
    val totalTimeSpentDead: Int,
    val totalUnitsHealed: Int,
    val tripleKills: Int,
    val trueDamageDealt: Int,
    val trueDamageDealtToChampions: Int,
    val trueDamageTaken: Int,
    val turretKills: Int,
    val turretTakedowns: Int,
    val turretsLost: Int,
    val unrealKills: Int,
    val visionScore: Int,
    val visionWardsBoughtInGame: Int,
    val wardsKilled: Int,
    val wardsPlaced: Int,
    val win: Boolean
)

data class TeamDto(
    val bans: List<BanDto>,
    val objectives: ObjectivesDto,
    val teamId: Int,
    val win: Boolean
)

data class BanDto(
    val championId: Int,
    val pickTurn: Int
)

data class ObjectivesDto(
    val baron: ObjectiveDto,
    val champion: ObjectiveDto,
    val dragon: ObjectiveDto,
    val inhibitor: ObjectiveDto,
    val riftHerald: ObjectiveDto,
    val tower: ObjectiveDto
)

data class ObjectiveDto(
    val first: Boolean,
    val kills: Int
) 