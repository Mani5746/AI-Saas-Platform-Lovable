package com.codingshuttleproject.lovableclone.mapper;

import com.codingshuttleproject.lovableclone.dto.Subscription.PlanResponse;
import com.codingshuttleproject.lovableclone.dto.Subscription.SubscriptionResponse;
import com.codingshuttleproject.lovableclone.entity.Plan;
import com.codingshuttleproject.lovableclone.entity.Subscription;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    SubscriptionResponse toSubscriptionResponse(Subscription subscription);
    PlanResponse toPlanResponse(Plan plan);
}
