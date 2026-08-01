package com.codingshuttleproject.lovableclone.service;

import com.codingshuttleproject.lovableclone.dto.Subscription.CheckoutRequest;
import com.codingshuttleproject.lovableclone.dto.Subscription.CheckoutResponse;
import com.codingshuttleproject.lovableclone.dto.Subscription.PortalResponse;
import com.stripe.model.StripeObject;

import java.util.Map;

public interface PaymentProcessor {
    CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request);

    PortalResponse openCustomerPortal(Long userId);

    void handleWebhookEvent(String type, StripeObject stripeObject, Map<String, String> metadata);
}
