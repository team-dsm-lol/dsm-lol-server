package com.dsm.dsmlolleague.controller

import com.dsm.dsmlolleague.dto.*
import com.dsm.dsmlolleague.service.RecruitService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/recruits")
@CrossOrigin(origins = ["*"])
class RecruitController(
    private val recruitService: RecruitService
) {
    
    @PostMapping
    fun sendRecruitRequest(
        @Valid @RequestBody recruitRequest: RecruitRequestDto,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<RecruitResponse>> {
        return try {
            val accountId = authentication.principal as String
            val recruitResponse = recruitService.sendRecruitRequest(accountId, recruitRequest)
            ResponseEntity.ok(ApiResponse.success(recruitResponse, "영입 요청 전송 성공"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "영입 요청 전송 실패"))
        }
    }
    
    @PostMapping("/{requestId}/respond")
    fun respondToRecruitRequest(
        @PathVariable requestId: Long,
        @Valid @RequestBody decision: RecruitDecisionRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val accountId = authentication.principal as String
            val message = recruitService.respondToRecruitRequest(accountId, requestId, decision)
            ResponseEntity.ok(ApiResponse.success(message))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "영입 요청 응답 실패"))
        }
    }
    
    @GetMapping("/pending")
    fun getPendingRequests(authentication: Authentication): ResponseEntity<ApiResponse<RecruitListResponse>> {
        return try {
            val accountId = authentication.principal as String
            val recruitListResponse = recruitService.getPendingRequestsForUser(accountId)
            ResponseEntity.ok(ApiResponse.success(recruitListResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "대기 중인 영입 요청 조회 실패"))
        }
    }
    
    @GetMapping("/team-requests")
    fun getTeamRecruitRequests(authentication: Authentication): ResponseEntity<ApiResponse<RecruitListResponse>> {
        return try {
            val accountId = authentication.principal as String
            val recruitListResponse = recruitService.getTeamRecruitRequests(accountId)
            ResponseEntity.ok(ApiResponse.success(recruitListResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "팀 영입 요청 조회 실패"))
        }
    }
    

} 