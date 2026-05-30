package com.mr486.msplatform.consumer;
import org.springframework.boot.SpringApplication;import org.springframework.boot.autoconfigure.SpringBootApplication;import org.springframework.amqp.rabbit.annotation.EnableRabbit;
@EnableRabbit
@SpringBootApplication
public class ConsumerApplication{public static void main(String[] args){SpringApplication.run(ConsumerApplication.class,args);}}
