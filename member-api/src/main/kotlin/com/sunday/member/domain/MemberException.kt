package com.sunday.member.domain

import com.sunday.common.exception.NotFoundException

sealed class MemberException(message: String) : RuntimeException(message)

class MemberNotFoundException(id: Long) :
    MemberException("회원을 찾을 수 없습니다: $id"),
    NotFoundException

class InvalidMemberNameException :
    MemberException("회원 이름은 비어있을 수 없습니다.")
