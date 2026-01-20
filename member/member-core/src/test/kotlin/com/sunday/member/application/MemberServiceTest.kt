package com.sunday.member.application

import com.sunday.member.domain.Member
import com.sunday.member.exception.MemberNotFoundException
import com.sunday.member.port.outbound.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class MemberServiceTest : DescribeSpec({

    val memberRepository = mockk<MemberRepository>()
    val memberService = MemberService(memberRepository)

    describe("getMemberById") {
        context("존재하는 회원 ID로 조회하면") {
            it("회원 정보를 반환한다") {
                val member = Member(id = 1L, name = "홍길동")
                every { memberRepository.findById(1L) } returns member

                val result = memberService.getMemberById(1L)

                result.id shouldBe 1L
                result.name shouldBe "홍길동"
                verify { memberRepository.findById(1L) }
            }
        }

        context("존재하지 않는 회원 ID로 조회하면") {
            it("MemberNotFoundException이 발생한다") {
                every { memberRepository.findById(999L) } returns null

                shouldThrow<MemberNotFoundException> {
                    memberService.getMemberById(999L)
                }
            }
        }
    }

    describe("getAllMembers") {
        context("회원이 존재하면") {
            it("전체 회원 목록을 반환한다") {
                val members = listOf(
                    Member(id = 1L, name = "홍길동"),
                    Member(id = 2L, name = "김철수")
                )
                every { memberRepository.findAll() } returns members

                val result = memberService.getAllMembers()

                result.size shouldBe 2
                result[0].name shouldBe "홍길동"
                result[1].name shouldBe "김철수"
            }
        }

        context("회원이 없으면") {
            it("빈 목록을 반환한다") {
                every { memberRepository.findAll() } returns emptyList()

                val result = memberService.getAllMembers()

                result.size shouldBe 0
            }
        }
    }

    describe("existsMember") {
        context("회원이 존재하면") {
            it("true를 반환한다") {
                every { memberRepository.existsById(1L) } returns true

                val result = memberService.existsMember(1L)

                result shouldBe true
            }
        }

        context("회원이 존재하지 않으면") {
            it("false를 반환한다") {
                every { memberRepository.existsById(999L) } returns false

                val result = memberService.existsMember(999L)

                result shouldBe false
            }
        }
    }

    describe("createMember") {
        context("유효한 이름으로 생성하면") {
            it("새로운 회원이 저장되고 반환된다") {
                val savedMember = Member(id = 1L, name = "홍길동")
                every { memberRepository.save(any()) } returns savedMember

                val result = memberService.createMember("홍길동")

                result.id shouldBe 1L
                result.name shouldBe "홍길동"
                verify { memberRepository.save(match { it.name == "홍길동" }) }
            }
        }
    }
})
