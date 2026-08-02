package com.codingshuttleproject.lovableclone.repository;

import com.codingshuttleproject.lovableclone.entity.Subscription;
import com.codingshuttleproject.lovableclone.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface SubscriptionRepository extends JpaRepository<Subscription,Long> {

    /*
    * Get the current active subscription
    **/
    Optional<Subscription>findByUserIdAndStatusIn(Long userId, Set<SubscriptionStatus> statusSet);

    boolean existsByStripeSubscriptionId(String subscriptionId);

    Optional<Subscription>findByStripeSubscriptionId(String gatewaySubscriptionId);
}
