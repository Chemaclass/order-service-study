package com.chemaclass.orderservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

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

@Entity(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
class Order {
    @Id
    @GeneratedValue
    private Long id;
    private Date datetime;
    private String state;

    Order(Date d, OrderStates os) {
        this.datetime = d;
        this.state = os.name();
    }

    public OrderStates getOrderState() {
        return OrderStates.valueOf(this.state);
    }

    public void setOrderState(OrderStates s) {
        this.state = s.name();
    }
}

interface OrderRepository extends JpaRepository<Order, Long> {
}

@Service
class OrderService {
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
