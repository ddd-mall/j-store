package com.jstore.user.client

import com.jstore.user.api.UserProfileInfo
import com.jstore.user.api.UserProfileQueryService
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

class UserProfileDependencyException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class HttpUserProfileQueryService(private val restClient: RestClient) : UserProfileQueryService {
    override fun findById(userId: Long): UserProfileInfo? {
        if (userId <= 0) return null
        return try {
            val profile =
                restClient
                    .get()
                    .uri("/internal/api/users/{userId}/profile", userId)
                    .retrieve()
                    .body(UserProfileInfo::class.java)
                    ?: throw UserProfileDependencyException(
                        "User profile service returned an empty body"
                    )
            if (profile.userId != userId) {
                throw UserProfileDependencyException(
                    "User profile service returned a mismatched user id"
                )
            }
            profile
        } catch (_: HttpClientErrorException.NotFound) {
            null
        } catch (error: UserProfileDependencyException) {
            throw error
        } catch (error: RestClientException) {
            throw UserProfileDependencyException("User profile service request failed", error)
        }
    }
}
