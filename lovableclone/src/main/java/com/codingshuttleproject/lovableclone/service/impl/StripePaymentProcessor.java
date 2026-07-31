package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.dto.Subscription.CheckoutRequest;
import com.codingshuttleproject.lovableclone.dto.Subscription.CheckoutResponse;
import com.codingshuttleproject.lovableclone.dto.Subscription.PortalResponse;
import com.codingshuttleproject.lovableclone.entity.Plan;
import com.codingshuttleproject.lovableclone.errors.ResourceNotFoundException;
import com.codingshuttleproject.lovableclone.repository.PlanRepository;
import com.codingshuttleproject.lovableclone.security.AuthUtil;
import com.codingshuttleproject.lovableclone.service.PaymentProcessor;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class StripePaymentProcessor implements PaymentProcessor {

    private final AuthUtil authUtil;
    private final PlanRepository planRepository;
    private final StripeClient stripeClient;
    @Value("${client.url}")
    private String frontendUrl;

    @Override
    public CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request) {
        Plan plan = planRepository.findById(request.planId()).orElseThrow(() ->
                new ResourceNotFoundException("Plan", request.planId().toString()));

        Long userId = authUtil.getCurrentUserId();

        SessionCreateParams params = SessionCreateParams.builder()
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(plan.getStripePriceId())
                                .setQuantity(1L)
                                .build())
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSubscriptionData(
                        new SessionCreateParams.SubscriptionData.Builder()
                                .setBillingMode(SessionCreateParams.SubscriptionData.BillingMode.builder()
                                .setType(SessionCreateParams.SubscriptionData.BillingMode.Type.FLEXIBLE)
                                        .build())
                                .build()
                )
                .setSuccessUrl(frontendUrl+ "/success.html?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl+ "/cancel.html?session_id={CHECKOUT_SESSION_ID}")
                .putMetadata("user_id",userId.toString())
                .putMetadata("plan_id",plan.getId().toString())
                .build();

        Session session;
        try {
            session=Session.create(params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Stripe checkout session", e);
        }

        return new CheckoutResponse(session.getUrl());
    }

    @Override
    public PortalResponse openCustomerPortal(Long userId) {
        return null;
    }
}