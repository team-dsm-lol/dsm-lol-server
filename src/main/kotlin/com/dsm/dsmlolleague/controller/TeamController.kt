package com.dsm.dsmlolleague.controller

import com.dsm.dsmlolleague.dto.*
import com.dsm.dsmlolleague.service.TeamService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = ["*"])
@Tag(name = "Team", description = "팀 관리 API")
class TeamController(
    private val teamService: TeamService
) {
    
    @PostMapping
    @Operation(summary = "팀 생성", description = "새로운 팀을 생성합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "팀 생성 성공"),
        SwaggerApiResponse(responseCode = "400", description = "팀 생성 실패")
    )
    fun createTeam(
        @Valid @RequestBody teamCreateRequest: TeamCreateRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<TeamResponse>> {
        return try {
            val accountId = authentication.principal as String
            val teamResponse = teamService.createTeam(accountId, teamCreateRequest)
            ResponseEntity.ok(ApiResponse.success(teamResponse, "팀 생성 성공"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "팀 생성 실패"))
        }
    }
    
    @GetMapping
    @Operation(summary = "팀 목록 조회", description = "모든 팀의 목록을 조회합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "팀 목록 조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "팀 목록 조회 실패")
    )
    fun getAllTeams(): ResponseEntity<ApiResponse<TeamListResponse>> {
        return try {
            val teamListResponse = teamService.getAllTeams()
            ResponseEntity.ok(ApiResponse.success(teamListResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "팀 목록 조회 실패"))
        }
    }
    
    @GetMapping("/{teamId}")
    @Operation(summary = "팀 상세 조회", description = "특정 팀의 상세 정보를 조회합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "팀 조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "팀 조회 실패")
    )
    fun getTeamById(@Parameter(description = "팀 ID") @PathVariable teamId: Long): ResponseEntity<ApiResponse<TeamResponse>> {
        return try {
            val teamResponse = teamService.getTeamById(teamId)
            ResponseEntity.ok(ApiResponse.success(teamResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "팀 조회 실패"))
        }
    }
    
    @GetMapping("/my-team")
    @Operation(summary = "내 팀 조회", description = "현재 로그인한 사용자의 팀 정보를 조회합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "내 팀 조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "내 팀 조회 실패")
    )
    fun getMyTeam(authentication: Authentication): ResponseEntity<ApiResponse<TeamResponse?>> {
        return try {
            val accountId = authentication.principal as String
            val teamResponse = teamService.getUserTeam(accountId)
            ResponseEntity.ok(ApiResponse.success(teamResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "내 팀 조회 실패"))
        }
    }
    
    @PostMapping("/leave")
    @Operation(summary = "팀 탈퇴", description = "현재 소속된 팀에서 탈퇴합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "팀 탈퇴 성공"),
        SwaggerApiResponse(responseCode = "400", description = "팀 탈퇴 실패")
    )
    fun leaveTeam(authentication: Authentication): ResponseEntity<ApiResponse<String>> {
        return try {
            val accountId = authentication.principal as String
            val message = teamService.leaveTeam(accountId)
            ResponseEntity.ok(ApiResponse.success(message))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "팀 탈퇴 실패"))
        }
    }
    
    @PostMapping("/kick/{userId}")
    @Operation(summary = "팀원 강퇴", description = "팀에서 특정 멤버를 강퇴합니다. (팀장만 가능)")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "팀원 강퇴 성공"),
        SwaggerApiResponse(responseCode = "400", description = "팀원 강퇴 실패")
    )
    fun kickMember(
        @Parameter(description = "강퇴할 사용자 ID") @PathVariable userId: Long,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val accountId = authentication.principal as String
            val message = teamService.kickMember(accountId, userId)
            ResponseEntity.ok(ApiResponse.success(message))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "팀원 강퇴 실패"))
        }
    }
    

} 