package com.dsm.dsmlolleague.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "teams")
data class Team(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, unique = true)
    val name: String,
    
    @OneToOne
    @JoinColumn(name = "leader_id", nullable = false)
    val leader: User,
    
    @OneToMany(mappedBy = "team", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val members: MutableList<User> = mutableListOf(),
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    val totalScore: Int
        get() = members.sumOf { it.score }
    
    val memberCount: Int
        get() = members.size
    
    fun isLeader(user: User): Boolean {
        return leader.id == user.id
    }
    
    fun canRecruit(): Boolean {
        return memberCount < 5
    }
} 