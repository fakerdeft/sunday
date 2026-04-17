package com.sunday.order.repository

import com.github.f4b6a3.tsid.TsidCreator
import org.hibernate.annotations.IdGeneratorType
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator

@IdGeneratorType(TsidIdentifierGenerator::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class Tsid

class TsidIdentifierGenerator : IdentifierGenerator {
    override fun generate(session: SharedSessionContractImplementor, obj: Any): Any =
        TsidCreator.getTsid256().toLong()
}
