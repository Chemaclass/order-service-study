package com.chemaclass.orderservice.entity;

import com.chemaclass.orderservice.states.OrderStates;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;

@Entity(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue
    private Long id;
    private Date datetime;
    private String state;

    public Order(Date d, OrderStates os) {
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
