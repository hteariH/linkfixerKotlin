package com.mamoru.repository

import com.mamoru.entity.UserCharacter
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserCharacterRepository : MongoRepository<UserCharacter, Long>
