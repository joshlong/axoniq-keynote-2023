package bootiful.axon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootApplication
public class AxonApplication {

    public static void main(String[] args) {
        SpringApplication.run(AxonApplication.class, args);
    }

}

@Controller
@ResponseBody
class Hello {

    @GetMapping("/hello")
    String hello() {
        return "Hello";
    }
}