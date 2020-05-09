package org.smartloli.kafka.eagle;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ImportResource("classpath:/spring/*.xml")
@MapperScan(basePackages = "org.smartloli.kafka.eagle.web.dao")
public class EagleApplication {
    public static void main(String[] args) {
        SpringApplication.run(EagleApplication.class,args);
    }
}
