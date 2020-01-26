package com.chemaclass.orderservice;

import lombok.extern.java.Log;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}

enum OrderEvents {
    FULFILL,
    PAY,
    CANCEL,
}

enum OrderStates {
    SUBMITTED,
    PAID,
    FULFILLED,
    CANCELLED,
}

@Log
@Component
class Runner implements ApplicationRunner {
    private final StateMachineFactory<OrderStates, OrderEvents> factory;

    Runner(StateMachineFactory<OrderStates, OrderEvents> factory) {
        this.factory = factory;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var orderId = 12345L;
        var machine = this.factory.getStateMachine(Long.toString(orderId));
        machine.getExtendedState().getVariables().put("orderId", orderId);
        machine.start();
        log.info("INIT state: " + machine.getState().getId().name());
        machine.sendEvent(OrderEvents.PAY);
        log.info("Current state: " + machine.getState().getId().name());

        var events = MessageBuilder
            .withPayload(OrderEvents.FULFILL)
            .setHeader("name", "value")
            .build();
        machine.sendEvent(events);
        log.info("Current state: " + machine.getState().getId().name());
    }
}

@Log
@Configuration
@EnableStateMachineFactory
class SimpleEnumStateMachineConfiguration extends StateMachineConfigurerAdapter<OrderStates, OrderEvents> {
    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions) throws Exception {
        transitions
            .withExternal().source(OrderStates.SUBMITTED).target(OrderStates.PAID).event(OrderEvents.PAY)
            .and()
            .withExternal().source(OrderStates.PAID).target(OrderStates.FULFILLED).event(OrderEvents.FULFILL)
            .and()
            .withExternal().source(OrderStates.PAID).target(OrderStates.CANCELLED).event(OrderEvents.CANCEL)
            .and()
            .withExternal().source(OrderStates.FULFILLED).target(OrderStates.CANCELLED).event(OrderEvents.CANCEL)
        ;
    }

    @Override
    public void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states) throws Exception {
        states
            .withStates()
            .initial(OrderStates.SUBMITTED)
            .stateEntry(OrderStates.SUBMITTED, context -> {
                Long orderId = (Long) context.getExtendedState().getVariables().getOrDefault("orderId", 1L);
                log.info("orderId is: " + orderId);
                log.info("entering ");
            })
            .state(OrderStates.FULFILLED)
            .end(OrderStates.FULFILLED)
            .end(OrderStates.CANCELLED)
        ;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderStates, OrderEvents> config) throws Exception {

        StateMachineListenerAdapter<OrderStates, OrderEvents> adapter = new StateMachineListenerAdapter<OrderStates, OrderEvents>() {
            @Override
            public void stateChanged(State<OrderStates, OrderEvents> from, State<OrderStates, OrderEvents> to) {
                log.info(String.format("stateChanged(from: %s, to: %s", from, to));
            }
        };

        config
            .withConfiguration()
            .autoStartup(false)
            .listener(adapter);
    }
}
