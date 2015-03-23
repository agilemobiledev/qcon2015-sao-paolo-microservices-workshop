package demo;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.SpringCloudApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@SpringCloudApplication
@EnableFeignClients
@EnableZuulProxy
public class PassportApplication {

    @Autowired
    void setMessage(@Value("${message}") String m) {
        System.out.println("message = " + m);
    }

    public static void main(String[] args) {
        SpringApplication.run(PassportApplication.class, args);
    }
}

@FeignClient("bookmark-service")
interface BookmarkClient {

    @RequestMapping(method = RequestMethod.GET, value = "/{userId}/bookmarks")
    Collection<Bookmark> getBookmarks(@PathVariable("userId") String userId);
}

@FeignClient("contact-service")
interface ContactClient {

    @RequestMapping(method = RequestMethod.GET, value = "/{userId}/contacts")
    Collection<Contact> getContacts(@PathVariable("userId") String userId);
}


@Order(1)
@Component
class DiscoveryClientExample implements CommandLineRunner {

    private Log log = LogFactory.getLog(this.getClass().getName());

    @Autowired
    private DiscoveryClient discoveryClient;

    @Override
    public void run(String... strings) throws Exception {
        log.info("------------------------------");
        log.info("DiscoveryClient Example");

        discoveryClient.getInstances("contact-service").forEach((ServiceInstance s) -> {
            System.out.println(ToStringBuilder.reflectionToString(s));
        });
        discoveryClient.getInstances("bookmark-service").forEach((ServiceInstance s) -> {
            System.out.println(ToStringBuilder.reflectionToString(s));
        });
    }
}

@Order(2)
@Component
class RestTemplateExample implements CommandLineRunner {

    private Log log = LogFactory.getLog(this.getClass().getName());

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void run(String... strings) throws Exception {

        log.info("------------------------------");
        log.info("RestTemplate Example");

        ParameterizedTypeReference<List<Bookmark>> responseType =
                new ParameterizedTypeReference<List<Bookmark>>() {
                };

        ResponseEntity<List<Bookmark>> exchange = this.restTemplate.exchange(
                "http://bookmark-service/{userId}/bookmarks",
                HttpMethod.GET, null, responseType, (Object) "pwebb");

        exchange.getBody().forEach(System.out::println);
    }
}

@Order(3)
@Component
class FeignExample implements CommandLineRunner {

    private Log log = LogFactory.getLog(this.getClass().getName());

    @Autowired
    private ContactClient contactClient;

    @Autowired
    private BookmarkClient bookmarkClient;

    @Override
    public void run(String... strings) throws Exception {

        log.info("------------------------------");
        log.info("Feign Example");

        this.bookmarkClient.getBookmarks("jlong").forEach(System.out::println);
        this.contactClient.getContacts("jlong").forEach(System.out::println);
    }
}

@Component
class IntegrationClient {

    @Autowired
    private ContactClient contactClient;

    @Autowired
    private BookmarkClient bookmarkClient;

    public Collection<Bookmark> getBookmarksFallback(String userId) {
        System.out.println("getBookmarksFallback");
        return Arrays.asList();
    }

    @HystrixCommand(fallbackMethod = "getBookmarksFallback")
    public Collection<Bookmark> getBookmarks(String userId) {
        return this.bookmarkClient.getBookmarks(userId);
    }

    public Collection<Contact> getContactsFallback(String userId) {
        System.out.println("getContactsFallback");
        return Arrays.asList();
    }

    @HystrixCommand(fallbackMethod = "getContactsFallback")
    public Collection<Contact> getContacts(String userId) {
        return this.contactClient.getContacts(userId);
    }


}


@RestController
class PassportRestController {

    @Autowired
    private IntegrationClient integrationClient;

    @RequestMapping("/{userId}/passport")
    Passport passport(@PathVariable String userId) {
        return new Passport(userId,
                this.integrationClient.getContacts(userId),
                this.integrationClient.getBookmarks(userId));
    }
}

class Passport {
    private String userId;
    private Collection<Bookmark> bookmarks;
    private Collection<Contact> contacts;

    public Passport(String userId,
                    Collection<Contact> contacts,
                    Collection<Bookmark> bookmarks) {
        this.userId = userId;
        this.bookmarks = bookmarks;
        this.contacts = contacts;
    }

    @Override
    public String toString() {
        return "Passport{" +
                "userId='" + userId + '\'' +
                ", bookmarks=" + bookmarks +
                ", contacts=" + contacts +
                '}';
    }

    public String getUserId() {
        return userId;
    }

    public Collection<Bookmark> getBookmarks() {
        return bookmarks;
    }

    public Collection<Contact> getContacts() {
        return contacts;
    }
}

class Contact {
    private Long id;

    private String userId, firstName, lastName, email;

    @Override
    public String toString() {
        return "Contact{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }
}

class Bookmark {
    private Long id;
    private String href, description, userId;

    public Bookmark() {
    }

    @Override
    public String toString() {
        return "Bookmark{" +
                "id=" + id +
                ", href='" + href + '\'' +
                ", description='" + description + '\'' +
                ", userId='" + userId + '\'' +
                '}';
    }

    public Long getId() {
        return id;
    }

    public String getHref() {
        return href;
    }

    public String getDescription() {
        return description;
    }

    public String getUserId() {
        return userId;
    }
}
