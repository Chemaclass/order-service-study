package com.chemaclass.orderservice.service;

import com.chemaclass.orderservice.states.OrderEvents;
import com.chemaclass.orderservice.states.OrderStates;
import com.chemaclass.orderservice.entity.Order;
import com.chemaclass.orderservice.repository.OrderRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
public class OrderService {
    public static final String ORDER_ID_HEADER = "orderId";
    private final OrderRepository repository;
    private final StateMachineFactory<OrderStates, OrderEvents> factory;

    OrderService(OrderRepository repository, StateMachineFactory<OrderStates, OrderEvents> factory) {
        this.repository = repository;
        this.factory = factory;
    }

    public Order create(Date when) {
        return repository.save(new Order(when, OrderStates.SUBMITTED));
    }

    public StateMachine<OrderStates, OrderEvents> change(Long orderId, OrderEvents events) {
        var machine = this.build(orderId);
        // TODO: any logic here...
        return machine;
    }

    public StateMachine<OrderStates, OrderEvents> fulfill(Long orderId) {
        var machine = this.build(orderId);
        var fulfillmentMessage = MessageBuilder.withPayload(OrderEvents.FULFILL)
            .setHeader(ORDER_ID_HEADER, orderId)
            .build();
        machine.sendEvent(fulfillmentMessage);

        return machine;
    }

    public StateMachine<OrderStates, OrderEvents> pay(Long orderId, String paymentConfirmationNumber) {
        var machine = this.build(orderId);
        var paymentMessage = MessageBuilder.withPayload(OrderEvents.PAY)
            .setHeader(ORDER_ID_HEADER, orderId)
            .setHeader("paymentConfirmationNumber", paymentConfirmationNumber)
            .build();
        machine.sendEvent(paymentMessage);

        return machine;
    }

    private StateMachine<OrderStates, OrderEvents> build(Long orderId) {
        Order order = this.repository.findById(orderId).get();
        var orderIdKey = Long.toString(orderId);
        var machine = this.factory.getStateMachine(orderIdKey);
        machine.stop();
        machine
            .getStateMachineAccessor()
            .doWithAllRegions(sma -> {
                sma.addStateMachineInterceptor(new StateMachineInterceptorAdapter<OrderStates, OrderEvents>() {
                    @Override
                    public void preStateChange(
                        State<OrderStates, OrderEvents> state,
                        Message<OrderEvents> message,
                        Transition<OrderStates, OrderEvents> transition,
                        StateMachine<OrderStates, OrderEvents> stateMachine
                    ) {
                        Optional.ofNullable(message)
                            .flatMap(msg -> Optional.ofNullable((Long) msg.getHeaders().getOrDefault(ORDER_ID_HEADER, 1L)))
                            .ifPresent(orderId -> {
                                Order order1 = repository.findById(orderId).get();
                                order1.setOrderState(state.getId());
                                repository.save(order1);
                            });
                    }
                });
                sma.resetStateMachine(
                    new DefaultStateMachineContext<OrderStates, OrderEvents>(
                        order.getOrderState(), null, null, null
                    ));
            });

        machine.start();

        return machine;
    }
}
