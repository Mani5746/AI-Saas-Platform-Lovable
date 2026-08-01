package com.codingshuttleproject.lovableclone.config;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {
    @Value("${stripe.api.secret}")
    private String stripesecretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripesecretKey;
    }

    @Bean
    public StripeClient stripeClient() {
        System.out.println("Stripe key loaded, starts with: " + stripesecretKey.substring(0, 7));
        return new StripeClient(stripesecretKey);
    }
}
