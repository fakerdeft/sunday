package com.sunday.member.domain

import com.sunday.member.exception.InvalidMemberNameException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MemberTest : DescribeSpec({

    describe("Member 생성") {
        context("유효한 이름이 주어지면") {
            it("Member가 정상 생성된다") {
                val member = Member(id = 1L, name = "홍길동")

                member.id shouldBe 1L
                member.name shouldBe "홍길동"
                member.createdAt shouldNotBe null
            }
        }

        context("이름이 비어있으면") {
            it("InvalidMemberNameException이 발생한다") {
                shouldThrow<InvalidMemberNameException> {
                    Member(id = 1L, name = "")
                }
            }

            it("공백만 있어도 예외가 발생한다") {
                shouldThrow<InvalidMemberNameException> {
                    Member(id = 1L, name = "   ")
                }
            }
        }
    }

    describe("Member.create") {
        context("유효한 이름으로 생성하면") {
            it("id가 0인 새로운 Member가 생성된다") {
                val member = Member.create("홍길동")

                member.id shouldBe 0L
                member.name shouldBe "홍길동"
            }
        }

        context("빈 이름으로 생성하면") {
            it("InvalidMemberNameException이 발생한다") {
                shouldThrow<InvalidMemberNameException> {
                    Member.create("")
                }
            }
        }
    }
})
