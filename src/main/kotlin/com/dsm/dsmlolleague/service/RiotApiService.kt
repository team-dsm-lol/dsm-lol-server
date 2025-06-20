package com.dsm.dsmlolleague.service

import com.dsm.dsmlolleague.entity.Tier
import com.dsm.dsmlolleague.entity.RankLevel
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Service
class RiotApiService(
    @Value("\${riot.api.key}")
    private val apiKey: String,
    
    @Value("\${riot.api.base-url}")
    private val baseUrl: String
) {
    
    // Riot API WebClient 설정 (장인 베네핏 계산용)
    private val httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
        .doOnConnected { conn ->
            conn.addHandlerLast(ReadTimeoutHandler(60, TimeUnit.SECONDS))
                .addHandlerLast(WriteTimeoutHandler(60, TimeUnit.SECONDS))
        }
    
    private val webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("X-Riot-Token", apiKey)
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .build()
    
    private val accountWebClient = WebClient.builder()
        .baseUrl("https://asia.api.riotgames.com")
        .defaultHeader("X-Riot-Token", apiKey)
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .build()
    
    /**
     * OP.GG에서 사용자의 티어 정보를 크롤링합니다.
     */
    fun getTierInfoFromOpGG(gameName: String, tagLine: String): OpGGTierInfo? {
        return try {
            val encodedName = URLEncoder.encode("$gameName-$tagLine", StandardCharsets.UTF_8)
            val url = "https://op.gg/ko/lol/summoners/kr/$encodedName?queue_type=SOLORANKED"
            
            val doc: Document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000)
                .get()
            
            // 이번 시즌 최고 티어 (text-sm first-letter:uppercase class를 가진 strong 태그)
            val currentSeasonElement = doc.selectFirst("strong.text-sm.first-letter\\:uppercase")
            val currentSeasonTier = currentSeasonElement?.text()?.trim()
            
            // 전시즌 최고 티어들 (text-xs lowercase first-letter:uppercase class를 가진 span 태그들)
            val previousSeasonElements = doc.select("span.text-xs.lowercase.first-letter\\:uppercase")
            val previousSeasonTiers = previousSeasonElements.map { it.text().trim() }
            
            println("OP.GG 크롤링 결과 - ${gameName}#${tagLine}")
            println("이번 시즌 최고: $currentSeasonTier")
            println("전시즌 티어들: $previousSeasonTiers")
            
            OpGGTierInfo(
                currentSeasonHighest = currentSeasonTier?.let { parseTierString(it) },
                previousSeasonTiers = previousSeasonTiers.mapNotNull { parseTierString(it) }
            )
            
        } catch (e: Exception) {
            println("OP.GG 크롤링 실패: ${e.message}")
            null
        }
    }
    
    /**
     * "gold 3" 형태의 문자열을 TierInfo로 변환합니다.
     */
    private fun parseTierString(tierString: String): TierInfo? {
        return try {
            val parts = tierString.lowercase().split(" ")
            if (parts.size != 2) return null
            
            val tierName = parts[0]
            val rankNumber = parts[1].toIntOrNull() ?: return null
            
            val tier = when (tierName) {
                "iron" -> Tier.IRON
                "bronze" -> Tier.BRONZE
                "silver" -> Tier.SILVER
                "gold" -> Tier.GOLD
                "platinum" -> Tier.PLATINUM
                "emerald" -> Tier.EMERALD
                "diamond" -> Tier.DIAMOND
                "master" -> Tier.MASTER
                "grandmaster" -> Tier.GRANDMASTER
                "challenger" -> Tier.CHALLENGER
                else -> return null
            }
            
            val rank = when (rankNumber) {
                1 -> RankLevel.I
                2 -> RankLevel.II
                3 -> RankLevel.III
                4 -> RankLevel.IV
                else -> null
            }
            
            TierInfo(tier, rank)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Riot ID로 PUUID를 조회합니다. (장인 베네핏 계산용)
     */
    private fun getPuuidByRiotId(gameName: String, tagLine: String): String? {
        return try {
            val accountDto = accountWebClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .bodyToMono(AccountDto::class.java)
                .block()
            
            accountDto?.puuid
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                null
            } else {
                throw RuntimeException("Riot Account API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * PUUID로 솔로랭크 매치 ID 목록을 조회합니다. (페이징 처리)
     * 시즌 필터링은 분석 단계에서 수행합니다.
     */
    private fun getSoloRankMatches(puuid: String, maxMatches: Int = 100): List<String> {
        return try {
            val allMatches = mutableListOf<String>()
            var start = 0
            val count = 100 // Riot API 최대 제한
            
            while (allMatches.size < maxMatches) {
                // Rate Limiting
                if (start > 0) {
                    Thread.sleep(100) // 100ms 딜레이
                }
                
                val matchIds = accountWebClient.get()
                    .uri("/lol/match/v5/matches/by-puuid/{puuid}/ids?start={start}&count={count}&queue=420", puuid, start, count)
                    .retrieve()
                    .bodyToMono(Array<String>::class.java)
                    .block()?.toList() ?: emptyList()
                
                if (matchIds.isEmpty()) break
                
                allMatches.addAll(matchIds)
                
                // 100개 미만이면 더 이상 매치가 없음
                if (matchIds.size < count) break
                
                start += count
            }
            
            println("솔로랭크 매치 ${allMatches.size}개 조회 완료")
            allMatches.take(maxMatches) // 최대 개수 제한
            
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 404) {
                emptyList()
            } else {
                throw RuntimeException("Match History API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 매치 ID로 매치 세부 정보를 조회합니다.
     */
    private fun getMatchDetails(matchId: String): MatchDto? {
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
                throw RuntimeException("Match Details API 요청 실패: ${e.message}")
            }
        }
    }
    
    /**
     * Riot API를 사용하여 현재 시즌 플레이 기록 기반 장인 베네핏을 계산합니다.
     * 
     * 장인 기준:
     * - 모스트 1개 90%: -4점
     * - 모스트 1개 80%: -3점  
     * - 모스트 2개 90%: -2점
     * - 모스트 2개 80%: -1점
     */
    fun calculateMasteryBenefitFromRiotAPI(gameName: String, tagLine: String): Int {
        return try {
            // 1. PUUID 조회
            val puuid = getPuuidByRiotId(gameName, tagLine)
            if (puuid == null) {
                println("PUUID 조회 실패: ${gameName}#${tagLine}")
                return 0
            }
            
            println("${gameName}#${tagLine} 장인 베네핏 계산 시작 (PUUID: ${puuid.take(8)}...)")
            
            // 2. 최근 100개 솔로랭크 매치 조회
            val matchIds = getSoloRankMatches(puuid, 100)
            if (matchIds.isEmpty()) {
                println("매치 기록이 없습니다")
                return 0
            }
            
            val championPlayCounts = mutableMapOf<Int, Int>()
            var totalGames = 0
            var currentSeasonGames = 0
            var requestCount = 0
            val currentSeasonStart = 1704067200000L // 2024-01-01 00:00:00 UTC
            
            println("최근 ${matchIds.size}개 매치 분석 시작 (현재 시즌 매치만 집계)...")
            
            // 3. 각 매치에서 플레이한 챔피언 카운트 (현재 시즌만)
            for (matchId in matchIds) {
                // Rate Limiting: 1초에 15개 요청 제한
                if (requestCount > 0) {
                    Thread.sleep(90) // 70ms 딜레이
                }
                
                val matchDetails = getMatchDetails(matchId) ?: continue
                requestCount++
                totalGames++
                
                // 이번 시즌 매치인지 확인 (2024년 1월 이후)
                val gameCreation = matchDetails.info.gameCreation
                if (gameCreation < currentSeasonStart) {
                    // 시간 순으로 정렬되어 있으므로 이전 시즌 매치를 만나면 더 이상 확인할 필요 없음
                    println("이전 시즌 매치 발견, 분석 종료 (총 ${totalGames}개 매치 확인)")
                    break
                }
                
                // 솔로랭크인지 재확인
                if (matchDetails.info.queueId != 420) continue
                
                // 해당 플레이어의 참가자 정보 찾기
                val participant = matchDetails.info.participants.find { it.puuid == puuid }
                if (participant != null) {
                    championPlayCounts[participant.championId] = 
                        championPlayCounts.getOrDefault(participant.championId, 0) + 1
                    currentSeasonGames++
                }
                
                // 진행상황 로그 (25게임마다)
                if (currentSeasonGames > 0 && currentSeasonGames % 25 == 0) {
                    println("현재 시즌 매치 ${currentSeasonGames}개 분석 완료...")
                }
            }
            
            if (currentSeasonGames == 0) {
                println("현재 시즌 솔로랭크 게임이 없습니다")
                return 0
            }
            
            // 4. 가장 많이 플레이한 챔피언들 정렬
            val sortedChampions = championPlayCounts.toList()
                .sortedByDescending { it.second }
                .take(2)
            
            if (sortedChampions.isEmpty()) return 0
            
            // 5. 플레이 비율 계산
            val firstChampionRate = sortedChampions[0].second.toDouble() / currentSeasonGames
            val secondChampionRate = if (sortedChampions.size > 1) {
                sortedChampions[1].second.toDouble() / currentSeasonGames
            } else 0.0
            
            println("장인 베네핏 계산 완료 - 최근 100개 중 현재 시즌 ${currentSeasonGames}게임 분석")
            println("1위 챔피언: ${(firstChampionRate * 100).toInt()}% (${sortedChampions[0].second}게임)")
            if (sortedChampions.size > 1) {
                println("2위 챔피언: ${(secondChampionRate * 100).toInt()}% (${sortedChampions[1].second}게임)")
            }
            
            // 6. 장인 베네핏 계산
            return when {
                // 모스트 1개 90% 이상 (최고 베네핏)
                firstChampionRate >= 0.9 -> {
                    println("모스트 1개 90% 이상: -4점")
                    -4
                }
                
                // 모스트 1개 80% 이상
                firstChampionRate >= 0.8 -> {
                    println("모스트 1개 80% 이상: -3점")
                    -3
                }
                
                // 모스트 2개 90% 이상 (1위 + 2위 합쳐서 90%)
                sortedChampions.size >= 2 && 
                (firstChampionRate + secondChampionRate) >= 0.9 -> {
                    println("모스트 2개 90% 이상: -2점")
                    -2
                }
                
                // 모스트 2개 80% 이상 (1위 + 2위 합쳐서 80%)
                sortedChampions.size >= 2 && 
                (firstChampionRate + secondChampionRate) >= 0.8 -> {
                    println("모스트 2개 80% 이상: -1점")
                    -1
                }
                
                else -> {
                    println("장인 베네핏 없음: 0점")
                    0
                }
            }
            
        } catch (e: Exception) {
            println("Riot API 장인 베네핏 계산 실패: ${e.message}")
            0
        }
    }
    
    /**
     * OP.GG 기반 장인 베네핏 계산 (사용 안함, Riot API 사용)
     */
    @Deprecated("Use calculateMasteryBenefitFromRiotAPI instead")
    fun calculateMasteryBenefitFromOpGG(gameName: String, tagLine: String): Int {
        // Riot API 사용으로 대체
        return calculateMasteryBenefitFromRiotAPI(gameName, tagLine)
    }
    
    fun calculateScore(
        seasonHighestTier: Tier?, 
        seasonHighestRank: RankLevel?, 
        allTimeHighestTier: Tier?, 
        allTimeHighestRank: RankLevel?, 
        level: Int?, 
        masteryBenefit: Int = 0
    ): Int {
        // 이번 시즌 최고 티어가 없는 경우 모든 시즌 최고 티어만 사용
        val baseTierInfo = if (seasonHighestTier == null) {
            TierInfo(allTimeHighestTier ?: Tier.IRON, allTimeHighestRank)
        } else {
            // 이번 시즌 최고 티어와 모든 시즌 최고 티어의 중간 티어 계산
            calculateMiddleTier(
                seasonHighestTier, seasonHighestRank,
                allTimeHighestTier ?: Tier.IRON, allTimeHighestRank
            )
        }
        
        // 중간 티어의 점수 계산
        val baseScore = calculateTierScore(baseTierInfo.tier, baseTierInfo.rank)
        
        // 레벨별 최소 점수 적용 (level이 null인 경우 0으로 처리)
        val actualLevel = level ?: 0
        val levelMinScore = when {
            actualLevel >= 300 -> 13
            actualLevel >= 200 -> 9
            actualLevel >= 100 -> 6
            else -> 0
        }
        
        // 최종 점수 = max(기본 점수, 레벨 최소 점수) + 장인 베네핏
        return maxOf(baseScore, levelMinScore) + masteryBenefit
    }
    
    /**
     * 두 티어 사이의 중간 티어를 계산합니다.
     */
    fun calculateMiddleTier(
        tier1: Tier, rank1: RankLevel?,
        tier2: Tier, rank2: RankLevel?
    ): TierInfo {
        val tierValue1 = getTierValue(tier1, rank1)
        val tierValue2 = getTierValue(tier2, rank2)
        val middleValue = (tierValue1 + tierValue2) / 2
        return getTierFromValue(middleValue)
    }
    
    private fun getTierValue(tier: Tier, rank: RankLevel?): Int {
        val tierBase = when (tier) {
            Tier.IRON -> 0
            Tier.BRONZE -> 4
            Tier.SILVER -> 8
            Tier.GOLD -> 12
            Tier.PLATINUM -> 16
            Tier.EMERALD -> 20
            Tier.DIAMOND -> 24
            Tier.MASTER -> 28
            Tier.GRANDMASTER -> 32
            Tier.CHALLENGER -> 36
        }
        
        val rankOffset = when (rank) {
            RankLevel.IV -> 0
            RankLevel.III -> 1
            RankLevel.II -> 2
            RankLevel.I -> 3
            null -> if (tier in listOf(Tier.MASTER, Tier.GRANDMASTER, Tier.CHALLENGER)) 0 else 2
        }
        
        return tierBase + rankOffset
    }
    
    private fun getTierFromValue(value: Int): TierInfo {
        return when (value) {
            in 0..3 -> TierInfo(Tier.IRON, getRankFromOffset(value % 4))
            in 4..7 -> TierInfo(Tier.BRONZE, getRankFromOffset(value % 4))
            in 8..11 -> TierInfo(Tier.SILVER, getRankFromOffset(value % 4))
            in 12..15 -> TierInfo(Tier.GOLD, getRankFromOffset(value % 4))
            in 16..19 -> TierInfo(Tier.PLATINUM, getRankFromOffset(value % 4))
            in 20..23 -> TierInfo(Tier.EMERALD, getRankFromOffset(value % 4))
            in 24..27 -> TierInfo(Tier.DIAMOND, getRankFromOffset(value % 4))
            in 28..31 -> TierInfo(Tier.MASTER, null)
            in 32..35 -> TierInfo(Tier.GRANDMASTER, null)
            else -> TierInfo(Tier.CHALLENGER, null)
        }
    }
    
    private fun getRankFromOffset(offset: Int): RankLevel {
        return when (offset) {
            0 -> RankLevel.IV
            1 -> RankLevel.III
            2 -> RankLevel.II
            3 -> RankLevel.I
            else -> RankLevel.III
        }
    }
    
    fun calculateTierScore(tier: Tier?, rank: RankLevel?): Int {
        return when (tier) {
            Tier.EMERALD -> when (rank) {
                RankLevel.I, RankLevel.II -> 22
                RankLevel.III, RankLevel.IV -> 20
                null -> 22
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
            Tier.IRON, null -> 3
            Tier.DIAMOND -> 25
            Tier.MASTER -> 30
            Tier.GRANDMASTER -> 35
            Tier.CHALLENGER -> 40
        }
    }
}

data class OpGGTierInfo(
    val currentSeasonHighest: TierInfo?,
    val previousSeasonTiers: List<TierInfo>
)

data class TierInfo(
    val tier: Tier,
    val rank: RankLevel?
)

// Riot API DTOs (장인 베네핏 계산용)
data class AccountDto(
    val puuid: String,
    val gameName: String,
    val tagLine: String
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
    val neutralMinionsKilled: Int?,
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