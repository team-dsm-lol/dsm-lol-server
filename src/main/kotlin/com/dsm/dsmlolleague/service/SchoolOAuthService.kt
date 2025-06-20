package com.dsm.dsmlolleague.service

import com.dsm.dsmlolleague.dto.SchoolLoginRequest
import com.dsm.dsmlolleague.dto.SchoolUserResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class SchoolOAuthService(
    @Value("\${school.oauth.base-url}")
    private val baseUrl: String,
    
    @Value("\${school.oauth.client-id}")
    private val clientId: String,
    
    @Value("\${school.oauth.client-secret}")
    private val clientSecret: String
) {
    
    private val webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .build()
    
    fun authenticateUser(loginRequest: SchoolLoginRequest): SchoolUserResponse? {
        return try {
            webClient.post()
                .uri("/user/user-data")
                .bodyValue(loginRequest)
                .retrieve()
                .bodyToMono(SchoolUserResponse::class.java)
                .block()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 404) {
                null // 인증 실패 또는 사용자 없음
            } else {
                throw RuntimeException("학교 인증 서버 오류: ${e.message}")
            }
        }
    }
} 