package com.mr486.msplatform.serviceb;
import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;import org.springframework.boot.autoconfigure.SpringBootApplication;
@EnableMongock
@SpringBootApplication
public class ServiceBApplication{public static void main(String[] args){SpringApplication.run(ServiceBApplication.class,args);}}
