package com.mamoru.repository

import com.mamoru.entity.UserBalance
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserBalanceRepository : MongoRepository<UserBalance, Long>
