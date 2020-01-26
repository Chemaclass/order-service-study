package com.chemaclass.orderservice;

import com.chemaclass.orderservice.service.OrderService;
import lombok.extern.java.Log;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Log
@Component
public class Runner implements ApplicationRunner {
    private final OrderService orderService;

    Runner(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var order = orderService.create(new Date());
        log.info("After calling create()");

        var paid = orderService.pay(order.getId(), UUID.randomUUID().toString());
        log.info("After calling pay()" + paid.getState().getId().name());

        var fulfilled = orderService.fulfill(order.getId());
        log.info("After calling fulfill()" + fulfilled.getState().getId().name());
    }
}
