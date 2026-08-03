package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.dto.Subscription.CheckoutRequest;
import com.codingshuttleproject.lovableclone.dto.Subscription.CheckoutResponse;
import com.codingshuttleproject.lovableclone.dto.Subscription.PortalResponse;
import com.codingshuttleproject.lovableclone.dto.Subscription.SubscriptionResponse;
import com.codingshuttleproject.lovableclone.entity.Plan;
import com.codingshuttleproject.lovableclone.entity.Subscription;
import com.codingshuttleproject.lovableclone.entity.User;
import com.codingshuttleproject.lovableclone.enums.SubscriptionStatus;
import com.codingshuttleproject.lovableclone.errors.ResourceNotFoundException;
import com.codingshuttleproject.lovableclone.mapper.SubscriptionMapper;
import com.codingshuttleproject.lovableclone.repository.PlanRepository;
import com.codingshuttleproject.lovableclone.repository.SubscriptionRepository;
import com.codingshuttleproject.lovableclone.repository.UserRepository;
import com.codingshuttleproject.lovableclone.security.AuthUtil;
import com.codingshuttleproject.lovableclone.service.SubscriptionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {
  private final AuthUtil authUtil;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionMapper subscriptionMapper;
  private final UserRepository userRepository;
  private final PlanRepository planRepository;

    @Override
    public SubscriptionResponse getCurrentSubscription() {
      Long userId=authUtil.getCurrentUserId();
        var currentSubscription= subscriptionRepository.findByUserIdAndStatusIn(userId, Set.of(
                SubscriptionStatus.ACTIVE,SubscriptionStatus.PAST_DUE,
                SubscriptionStatus.TRIALING
        )).orElse(
                new Subscription()
        );

        return subscriptionMapper.toSubscriptionResponse(currentSubscription);
    }

    @Override
    public void activateSubscription(Long userId, Long planId, String subscriptionId, String customerId) {
      boolean exists=subscriptionRepository.existsByStripeSubscriptionId(subscriptionId);
      if(exists){ return;}

      User user=getUser(userId);
      Plan plan=getPlan(planId);

      Subscription subscription=Subscription.builder()
              .user(user)
              .plan(plan)
              .stripeSubscriptionId(subscriptionId)
              .status(SubscriptionStatus.INCOMPLETE)
              .build();
      subscriptionRepository.save(subscription);

    }

    @Override
    @Transactional
    public void updateSubscription(String gatewaySubscriptionId, SubscriptionStatus status, Instant periodStart, Instant periodEnd, Boolean cancelAtPeriodEnd, Long planId) {
      Subscription subscription=getSubscription(gatewaySubscriptionId);
      Boolean hassubscriptionUpdated=false;
      if(status!=null && status !=subscription.getStatus()){
          subscription.setStatus(status);
          hassubscriptionUpdated=true;
      }

      if(periodStart!=null && !periodStart.equals(subscription.getCurrentPeriodStart())){
          subscription.setCurrentPeriodStart(periodStart);
          hassubscriptionUpdated=true;
      }

      if(periodEnd!=null && !periodEnd.equals(subscription.getCurrentPeriodEnd())){
          subscription.setCurrentPeriodEnd(periodEnd);
          hassubscriptionUpdated=true;
      }

      if(cancelAtPeriodEnd!=null && !cancelAtPeriodEnd.equals(subscription.getCurrentPeriodEnd())){
          subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
          hassubscriptionUpdated=true;
      }
      if(planId!=null && !planId.equals(subscription.getPlan().getId())){
          Plan plan=getPlan(planId);
          subscription.setPlan(plan);
          hassubscriptionUpdated=true;
      }
      if(hassubscriptionUpdated){
          log.debug("Subscription has been updated : {}", gatewaySubscriptionId);
          subscriptionRepository.save(subscription);
      }

    }

    @Override
    public void cancelSubscription(String gatewaySubscriptionId) {
       Subscription subscription=getSubscription(gatewaySubscriptionId);
       subscription.setStatus(SubscriptionStatus.CANCELLED);
       subscriptionRepository.save(subscription);
    }

    @Override
    public void renewSubscriptionPeriod(String gatewaySubscriptionId, Instant periodStart, Instant periodEnd) {
     Subscription subscription=getSubscription(gatewaySubscriptionId);
     Instant newStart = periodStart!=null?periodStart:subscription.getCurrentPeriodEnd();
     subscription.setCurrentPeriodStart(newStart);
     subscription.setCurrentPeriodEnd(periodEnd);

     if(subscription.getStatus() == SubscriptionStatus.PAST_DUE || subscription.getStatus() == SubscriptionStatus.INCOMPLETE){
         subscription.setStatus(SubscriptionStatus.ACTIVE);
     }
     subscriptionRepository.save(subscription);
    }

    @Override
    public void markSubscriptionPastDue(String gatewaySubscriptionId) {
        Subscription subscription=getSubscription(gatewaySubscriptionId);
        if(subscription.getStatus() == SubscriptionStatus.PAST_DUE){
            log.debug("Subscription Past Due, gatewaySubscriptionId:{}",gatewaySubscriptionId);
            return;
        }
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(subscription);
        // Notify User via email
    }

// Utility methods

    private User getUser(Long userId) {
        return userRepository.findById(userId).
                orElseThrow(()-> new ResourceNotFoundException("User",userId.toString()));
    }

    private Plan getPlan(Long planId) {
        return planRepository.findById(planId).
                orElseThrow(()-> new ResourceNotFoundException("Plan",planId.toString()));
    }

    private Subscription getSubscription(String gatewaySubscriptionId) {
        return subscriptionRepository.findByStripeSubscriptionId(gatewaySubscriptionId).
                orElseThrow(() -> new ResourceNotFoundException("Subscription", gatewaySubscriptionId));
    }
}
