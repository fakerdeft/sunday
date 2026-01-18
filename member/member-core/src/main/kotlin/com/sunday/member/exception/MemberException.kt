package com.sunday.member.exception

import com.sunday.common.exception.AlreadyExistsException
import com.sunday.common.exception.NotFoundException

/**
 * Member 도메인 예외
 */
sealed class MemberException(message: String) : RuntimeException(message)

class MemberNotFoundException(id: Long) :
    MemberException("회원을 찾을 수 없습니다: $id"),
    NotFoundException

class MemberAlreadyExistsException(name: String) :
    MemberException("이미 존재하는 회원 이름입니다: $name"),
    AlreadyExistsException

class InvalidMemberNameException :
    MemberException("회원 이름은 비어있을 수 없습니다.")
