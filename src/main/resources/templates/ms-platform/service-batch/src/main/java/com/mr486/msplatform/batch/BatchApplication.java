package com.mr486.msplatform.batch;
import org.springframework.boot.SpringApplication;import org.springframework.boot.autoconfigure.SpringBootApplication;import org.springframework.amqp.rabbit.annotation.EnableRabbit;
@EnableRabbit
@SpringBootApplication
public class BatchApplication{public static void main(String[] args){SpringApplication.run(BatchApplication.class,args);}}
