package com.dsm.dsmlolleague.service

import com.dsm.dsmlolleague.entity.Tier
import com.dsm.dsmlolleague.entity.RankLevel
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class RiotApiService(
    @Value("\${riot.api.key}")
    private val apiKey: String,
    
    @Value("\${riot.api.base-url}")
    private val baseUrl: String
) {
    
    private val webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("X-Riot-Token", apiKey)
        .build()
    
    private val accountWebClient = WebClient.builder()
        .baseUrl("https://asia.api.riotgames.com") // 아시아 지역 (한국 포함)
        .defaultHeader("X-Riot-Token", apiKey)
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
    
    fun calculateScore(tier: Tier, rank: RankLevel?, leaguePoints: Int): Int {
        val tierScoreMap = mapOf(
            Tier.IRON to 500,
            Tier.BRONZE to 700,
            Tier.SILVER to 900,
            Tier.GOLD to 1100,
            Tier.PLATINUM to 1300,
            Tier.EMERALD to 1500,
            Tier.DIAMOND to 1700,
            Tier.MASTER to 2000,
            Tier.GRANDMASTER to 2200,
            Tier.CHALLENGER to 2500
        )
        
        return tierScoreMap[tier]!! + leaguePoints
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