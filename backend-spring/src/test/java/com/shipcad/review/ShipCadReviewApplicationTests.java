package com.shipcad.review;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-context-test;DB_CLOSE_DELAY=-1",
        "spring.profiles.active=test",
        "shipcad.security.seed-dev-users=false"
})
class ShipCadReviewApplicationTests {
    @Test
    void contextLoads() {
    }
}
