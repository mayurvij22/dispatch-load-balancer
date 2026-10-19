package com.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Dispatch Load Balancer service.
 */
@SpringBootApplication
public class DispatchLoadBalancerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchLoadBalancerApplication.class, args);
    }
}
