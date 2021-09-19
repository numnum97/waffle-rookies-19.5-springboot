package com.wafflestudio.seminar.domain.seminar.service

import com.wafflestudio.seminar.domain.seminar.dto.SeminarDto
import com.wafflestudio.seminar.domain.seminar.exception.*
import com.wafflestudio.seminar.domain.seminar.model.Seminar
import com.wafflestudio.seminar.domain.seminar.model.SeminarParticipant
import com.wafflestudio.seminar.domain.seminar.repository.SeminarRepository
import com.wafflestudio.seminar.domain.user.dto.UserDto
import com.wafflestudio.seminar.domain.user.exception.InvalidRoleRequestException
import com.wafflestudio.seminar.domain.user.exception.UserNotFoundException
import com.wafflestudio.seminar.domain.user.model.Role
import com.wafflestudio.seminar.domain.user.model.User
import com.wafflestudio.seminar.domain.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
@Transactional
class SeminarService(
    private val seminarRepository: SeminarRepository,
    private val userRepository: UserRepository,
) {
    fun register(registerRequest: SeminarDto.RegisterRequest, user: User): Seminar {
        if (!user.roles.contains(Role.INSTRUCTOR.role)) throw NotInstructorException("NOT_INSTRUCTOR")

        if (!isTimeFormatValid(registerRequest.time)) throw InvalidTimeRequestException("WRONG TIME FORMAT")

        val seminar = Seminar(registerRequest.name, registerRequest.capacity, registerRequest.count, registerRequest.time)
        when (registerRequest.online?.toLowerCase()) {
            "true" -> seminar.online = true
            "false" -> seminar.online = false
            null -> seminar.online = true
            else -> throw InvalidOnlineRequestException("Online should be 'true' or 'false'")
        }

        // Dirty Checking 을 위해 User 다시 조회
        val findUser = userRepository.findByIdOrNull(user.id) ?: throw UserNotFoundException("USER NOT FOUND")
        findUser.instructorProfile?.seminar = seminar

        seminar.instructorProfile.add(findUser.instructorProfile!!)

        return seminarRepository.save(seminar)
    }

    fun update(seminarId: Long, updateRequest: SeminarDto.UpdateRequest, user: User): Seminar {
        val seminar = seminarRepository.findByIdOrNull(seminarId) ?: throw SeminarNotFoundException("SEMINAR NOT FOUND")

        if (user.id != seminar.instructorProfile[0].user!!.id) throw NotChargeException("You are not charger")

        if (updateRequest.count != null) seminar.count = updateRequest.count

        if (updateRequest.time != null) {
            if (!isTimeFormatValid(updateRequest.time)) throw InvalidTimeRequestException("WRONG TIME FORMAT")
            seminar.time = updateRequest.time
        }

        if (updateRequest.online != null) {
            when (updateRequest.online.toLowerCase()) {
                "true" -> seminar.online = true
                "false" -> seminar.online = false
                else -> throw InvalidOnlineRequestException("Online should be 'true' or 'false'")
            }
        }

        // TODO: 2021-09-18 is_active 에 관한 것
        if (updateRequest.capacity != null) {
            if (updateRequest.capacity < seminar.seminarParticipants.size) {
                throw InvalidCapacityRequestException("CAPACITY TOO SMALL")
            }
            seminar.capacity = updateRequest.capacity
        }

        return seminar
    }

    fun getSeminarById(seminarId: Long): Seminar {
        return seminarRepository.findByIdOrNull(seminarId) ?: throw SeminarNotFoundException("SEMINAR NOT FOUND")
    }

    // TODO: 2021-09-19 refactoring
    fun getSeminarsByQueryParams(allParams: Map<String, String>): List<Seminar> {

        return if (allParams.keys.containsAll(listOf("name", "order"))) {
            if (allParams["order"] == "earliest") {
                seminarRepository.findByNameContainingOrderByCreatedAtAsc(allParams["name"]!!)
            } else {
                seminarRepository.findByNameContainingOrderByCreatedAtDesc(allParams["name"]!!)
            }
        } else if (allParams.keys.contains("name")) {
            seminarRepository.findByNameContainingOrderByCreatedAtDesc(allParams["name"]!!)
        } else if (allParams.keys.contains("order")) {
            if (allParams["order"] == "earliest") {
                seminarRepository.findAllByOrderByCreatedAtAsc()
            } else {
                seminarRepository.findAllByOrderByCreatedAtDesc()
            }
        } else {
            seminarRepository.findAllByOrderByCreatedAtDesc()
        }

    }

    fun enterSeminarLater(seminarId: Long, enterRequest: SeminarDto.EnterRequest, user: User): Seminar {
        val seminar = seminarRepository.findByIdOrNull(seminarId) ?: throw SeminarNotFoundException("SEMINAR NOT FOUND")
        val findUser = userRepository.findByIdOrNull(user.id) ?: throw UserNotFoundException()

        val requestRole = enterRequest.role
        if (requestRole != Role.PARTICIPANT.role && requestRole != Role.INSTRUCTOR.role) {
            throw InvalidRoleRequestException("Role should be 'instructor' or 'participant'")
        }
        if (!user.roles.contains(requestRole)) throw NotRoleSuitableException("ROLE NOT SUITABLE")

        if (seminar.capacity <= seminar.seminarParticipants.size) throw AlreadyFullException("SEMINAR ALREADY FULL")

        if (seminar.seminarParticipants.any { it.participantProfile.user?.id == user.id }
            || seminar.instructorProfile.any { it.user?.id == user.id }) {
            throw AlreadyEnteredException("ALREADY IN SEMINAR")
        }

        when(requestRole) {
            Role.PARTICIPANT.role -> {
                user.participantProfile ?: throw NotRoleSuitableException("ROLE NOT SUITABLE")
                if(!user.participantProfile!!.accepted) throw NotAcceptedException("CANNOT PARTICIPATE")

                val seminarParticipant = SeminarParticipant(seminar, findUser.participantProfile!!)
                seminar.seminarParticipants.add(seminarParticipant)
                findUser.participantProfile!!.seminars.add(seminarParticipant)
            }
            Role.INSTRUCTOR.role -> {
                user.instructorProfile ?: throw NotRoleSuitableException("ROLE NOT SUITABLE")
                if(user.instructorProfile?.seminar != null) throw AlreadyChargeException("You're charged")

                findUser.instructorProfile!!.seminar = seminar
                seminar.instructorProfile.add(findUser.instructorProfile!!)
            }
        }

        return seminar
    }

    private fun isTimeFormatValid(time: String): Boolean {
        val regex = "^([1-9]|[01][0-9]|2[0-3]):([0-5][0-9])$".toRegex()
        return time.matches(regex)
    }




}