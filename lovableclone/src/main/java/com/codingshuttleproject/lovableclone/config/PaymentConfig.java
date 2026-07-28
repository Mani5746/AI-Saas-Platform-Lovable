package com.codingshuttleproject.lovableclone.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {
    @Value("${stripe.secret}")
    private String stripesecretKey;

    @PostConstruct
    public void init(){
        Stripe.apiKey=stripesecretKey;
    }

}
