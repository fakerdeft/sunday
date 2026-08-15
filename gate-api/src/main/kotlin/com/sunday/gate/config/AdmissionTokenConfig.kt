package com.sunday.gate.config

import com.sunday.common.admission.AdmissionTokenCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AdmissionTokenConfig {

    @Bean
    fun admissionTokenCodec(
        @Value("\${sunday.order-pass.admission-secret}") secret: String
    ): AdmissionTokenCodec = AdmissionTokenCodec(secret)
}
