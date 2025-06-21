package com.dsm.dsmlolleague.controller

import com.dsm.dsmlolleague.dto.*
import com.dsm.dsmlolleague.entity.Tier
import com.dsm.dsmlolleague.service.UserService
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
@RequestMapping("/api/users")
@CrossOrigin(origins = ["*"])
@Tag(name = "User", description = "사용자 관리 API")
class UserController(
    private val userService: UserService
) {
    
    @PostMapping("/login")
    @Operation(summary = "학교 계정 로그인", description = "DSM 학교 계정으로 로그인하여 JWT 토큰을 발급받습니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "로그인 성공"),
        SwaggerApiResponse(responseCode = "400", description = "로그인 실패")
    )
    fun login(@Valid @RequestBody loginRequest: SchoolLoginRequest): ResponseEntity<ApiResponse<Map<String, String>>> {
        return try {
            val token = userService.loginWithSchoolAccount(loginRequest)
            val response = mapOf("token" to token)
            ResponseEntity.ok(ApiResponse.success(response, "로그인 성공"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "로그인 실패"))
        }
    }
    
    @PostMapping("/register-riot")
    @Operation(
        summary = "Riot 계정 연동", 
        description = "Riot ID와 선호 라인을 설정하여 Riot 계정을 사용자 계정과 연동합니다. 예: {'gameName': 'Hide on bush', 'tagLine': 'KR1', 'mostLane': 'MID', 'secondLane': 'ADC'}"
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Riot 계정 연동 성공"),
        SwaggerApiResponse(responseCode = "400", description = "Riot 계정 연동 실패")
    )
    fun registerRiotAccount(
        @Valid @RequestBody riotAccountRequest: RiotAccountRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<UserResponse>> {
        return try {
            val accountId = authentication.principal as String
            val userResponse = userService.registerRiotAccount(accountId, riotAccountRequest)
            ResponseEntity.ok(ApiResponse.success(userResponse, "Riot 계정 연동 성공"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "Riot 계정 연동 실패"))
        }
    }
    
    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "사용자 정보 조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "사용자 정보 조회 실패")
    )
    fun getMyInfo(authentication: Authentication): ResponseEntity<ApiResponse<UserResponse>> {
        return try {
            val accountId = authentication.principal as String
            val userResponse = userService.getUserByAccountId(accountId)
            ResponseEntity.ok(ApiResponse.success(userResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "사용자 정보 조회 실패"))
        }
    }
    
    @GetMapping
    @Operation(summary = "사용자 목록 조회", description = "조건에 따라 사용자 목록을 조회합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "사용자 목록 조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "사용자 목록 조회 실패")
    )
    fun getAllUsers(
        @Parameter(description = "티어 필터") @RequestParam(required = false) tier: Tier?,
        @Parameter(description = "이름 필터") @RequestParam(required = false) name: String?,
        @Parameter(description = "팀 소속 여부 필터") @RequestParam(required = false) hasTeam: Boolean?
    ): ResponseEntity<ApiResponse<UserListResponse>> {
        return try {
            val filter = UserFilterRequest(tier, name, hasTeam)
            val userListResponse = userService.getAllUsers(filter)
            ResponseEntity.ok(ApiResponse.success(userListResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "사용자 목록 조회 실패"))
        }
    }
    
    @GetMapping("/available")
    @Operation(summary = "영입 가능한 사용자 조회", description = "팀에 영입 가능한 사용자 목록을 조회합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "영입 가능한 사용자 목록 조회 성공"),
        SwaggerApiResponse(responseCode = "400", description = "영입 가능한 사용자 목록 조회 실패")
    )
    fun getAvailableUsers(
        @Parameter(description = "티어 필터") @RequestParam(required = false) tier: Tier?,
        @Parameter(description = "이름 필터") @RequestParam(required = false) name: String?
    ): ResponseEntity<ApiResponse<UserListResponse>> {
        return try {
            val userListResponse = userService.getAvailableUsers(tier, name)
            ResponseEntity.ok(ApiResponse.success(userListResponse))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "영입 가능한 사용자 목록 조회 실패"))
        }
    }
    
    @PutMapping("/me/top-tier")
    @Operation(summary = "내 모든 시즌 최고 티어 업데이트", description = "모든 시즌 최고 티어 정보를 수동으로 업데이트합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "모든 시즌 최고 티어 정보 업데이트 성공"),
        SwaggerApiResponse(responseCode = "400", description = "모든 시즌 최고 티어 정보 업데이트 실패")
    )
    fun updateMyTopTier(
        @Valid @RequestBody topTierRequest: TopTierUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<UserResponse>> {
        return try {
            val accountId = authentication.principal as String
            val userResponse = userService.updateTopTierInfo(accountId, topTierRequest.topTier, topTierRequest.topRank)
            ResponseEntity.ok(ApiResponse.success(userResponse, "모든 시즌 최고 티어 정보 업데이트 성공"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "모든 시즌 최고 티어 정보 업데이트 실패"))
        }
    }
    
    @PostMapping("/admin/recalculate-scores")
    @Operation(summary = "모든 사용자 점수 재계산 (관리자)", description = "모든 사용자의 점수를 최신 정보로 재계산합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "점수 재계산 성공"),
        SwaggerApiResponse(responseCode = "400", description = "점수 재계산 실패")
    )
    fun recalculateAllScores(): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = userService.recalculateAllScores()
            ResponseEntity.ok(ApiResponse.success(result, "점수 재계산 완료"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "점수 재계산 실패"))
        }
    }
    
    @PostMapping("/admin/analyze-season-tiers")
    @Operation(summary = "모든 사용자 시즌별 최고 티어 분석 (관리자)", description = "매치 히스토리를 분석하여 모든 사용자의 과거 시즌 최고 티어를 추정합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "시즌별 티어 분석 성공"),
        SwaggerApiResponse(responseCode = "400", description = "시즌별 티어 분석 실패")
    )
    fun analyzeSeasonTiers(): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = userService.analyzeAllUsersSeasonTiers()
            ResponseEntity.ok(ApiResponse.success(result, "시즌별 티어 분석 완료"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "시즌별 티어 분석 실패"))
        }
    }
    
    @PostMapping("/admin/update-levels")
    @Operation(summary = "모든 사용자 레벨 업데이트 (관리자)", description = "모든 사용자의 실제 소환사 레벨을 Riot API에서 조회하여 업데이트하고 점수를 재계산합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "레벨 업데이트 성공"),
        SwaggerApiResponse(responseCode = "400", description = "레벨 업데이트 실패")
    )
    fun updateAllUsersLevel(): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = userService.updateAllUsersLevel()
            ResponseEntity.ok(ApiResponse.success(result, "레벨 업데이트 완료"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "레벨 업데이트 실패"))
        }
    }

} 