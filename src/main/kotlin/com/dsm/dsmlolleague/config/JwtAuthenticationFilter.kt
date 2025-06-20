package com.dsm.dsmlolleague.config

import com.dsm.dsmlolleague.service.JwtTokenService
import com.dsm.dsmlolleague.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val authHeader = request.getHeader("Authorization")
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.substring(7)
                
                if (jwtTokenService.validateToken(token)) {
                    val accountId = jwtTokenService.getAccountIdFromToken(token)
                    val user = userRepository.findByAccountId(accountId)
                    
                    if (user != null && SecurityContextHolder.getContext().authentication == null) {
                        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
                        val authentication = UsernamePasswordAuthenticationToken(
                            accountId,
                            null,
                            authorities
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authentication
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("JWT 토큰 처리 중 오류 발생: ${e.message}")
        }
        
        filterChain.doFilter(request, response)
    }
} 