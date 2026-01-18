package com.sunday.payment.adapter.outbound

import com.sunday.account.port.inbound.AccountUseCase
import com.sunday.payment.port.outbound.AccountPort
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class AccountPortAdapter(
    private val accountUseCase: AccountUseCase
) : AccountPort {

    override fun getBalance(memberId: Long): BigDecimal {
        val account = accountUseCase.getAccountByMemberId(memberId)

        return account.balance
    }

    override fun withdraw(memberId: Long, amount: BigDecimal, description: String) {
        val account = accountUseCase.getAccountByMemberId(memberId)
        accountUseCase.withdraw(account.id, amount, description)
    }

    override fun deposit(memberId: Long, amount: BigDecimal, description: String) {
        val account = accountUseCase.getAccountByMemberId(memberId)
        accountUseCase.deposit(account.id, amount, description)
    }
}
