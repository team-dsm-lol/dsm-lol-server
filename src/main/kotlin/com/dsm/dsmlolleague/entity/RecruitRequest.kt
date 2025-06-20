package com.dsm.dsmlolleague.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "recruit_requests")
data class RecruitRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne
    @JoinColumn(name = "team_id", nullable = false)
    val team: Team,
    
    @ManyToOne
    @JoinColumn(name = "target_user_id", nullable = false)
    val targetUser: User,
    
    @ManyToOne
    @JoinColumn(name = "requester_id", nullable = false)
    val requester: User,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RecruitStatus = RecruitStatus.PENDING,
    
    @Column
    var message: String? = null,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class RecruitStatus {
    PENDING, ACCEPTED, REJECTED
} 