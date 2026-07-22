# Interview Questions

## Java 

----
- Q1: List me some java collections that you use frequently
----


----
- Q2: In the following code sample, do you think the method will perform the same if the passed list is array list or linked list ? 
	+ How to fix the method code so that no matter the implementation of the list that is passed, we always end up with the same time complexity  

```java
public void processLogs(List<LogItem> logs) {
    for (int i = 0; i < logs.size(); i++) {
        LogItem log = logs.get(i);
		if(log.getDate().greaterThen(yesterday) && log.getLevel.equals("ERROR")){
            storeInOpenSearch(log);
		}
    }
}
```
- Q2/Solution: for the fix, either use enhanced loop or use iterator. 
----


----
- Q3: What is the difference between java HashMap and TreeMap ?
- Q3/Solution: HashMap : O(1) constant time for basic operations, while with TreeMap it's O(log(n)), treeMap is usefull when we need to maintain natural order of the keys. If you need order of insertion => LinkedHashMap
----


----
- Q4: How does java HashMap work under the hood ? 
	+ What happens if i insert an element that has the same hash as another one already inserted (Hash collision)

- Q4/Solution: Hashmap uses an array of buckets, each bucket may contain 0, 1 or more elements.
	+ m.put(key,value) => key.hashCode() to figure out the bucket index, then compares if the key is equal to any key already existing in the bucket, if yes then replace, else add the element to list, if list grows beyound a certain thrushhold, convert it to a tree to keep operations at o(log(n))
----


----
- Q5: Lets say in a web service, i want to maintain in cache a map of authentication tokens, as key, it will be the token, and as value, details about the user that sent the token. What implementation of Map works better for my use case


- Q5/Solution: ConcurrentHashMap because it's thread safe and supports parial locking.
----


---- 
- Q6: Can you spot a problem in the following code:

````java
public String sanitizeMessage(String rawContent) {
    String cleanMessage = "";
    
    for (char c : rawContent.toCharArray()) {
        if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
            cleanMessage += c;
        }
    }
    
    return cleanMessage;
}
```` 

- Q6/Solution: Use StringBuilder instead
	+ follow up: what is the role of the garbage collector, do you need to call it manually?
----


----
- Q7: For you, what is the difference between an abstract class and an interface in java ?
- Q7/Solution: an interface can only have abstract methods, or default methods. it cant have a constructor, or instance variables. With abstract class you can have instance variable, you can have concrete impelmentation. a class can implement multiple interfaces but only one abstract class. 
	+ follow up: can a class implement multiple interfaces in java ? 

----




----
- Q8: difference final and finally keywords in java ? 
----


----
- Q9: what does the keyword 'synchronized' do ?
- Q9/Solution: ensure mutual exclusion: only one thread can access a piece of code at a time.
----



----
- Q10: What's a dead lock ? how to prevent it ? 
- Q10/Solution: two threads blocked forever, each waiting to aquire lock held by the other
	+ to prevent it: always aquire locks in order, always set a timeout on lock aquisition
----


---- 
- Q11: What is the risk with this kind of algorithm ? (ignore the public fields and lack of getters/setters)

````java
class Node {
    public int val;
    public Node left, right;

    public Node(int val) {
        this.val = val;
    }
}

class TreeTraversal {
    public void traverse(Node root) {
        if (root == null) return;
        System.out.println(root.val);
        traverse(root.left);
        traverse(root.right);
    }
}
````
- Q11/Solution: StackOverflowError if the tree is very deep 
	+ how to avoid this ? => replace recursion with iterative approach, using Stack datasructure (LIFO)
		+ ArrayDeque implments Dequeu is a better alternative then Stack.
		+ Deque supports insertion and removal at both ends but can be used as a stack (lifo)
		+ LinkedList implements Deque.
----


----
- Q12: Explain the different memory regions in a JVM (heap vs stack)
- Q12/Solution:
	+ Stack: one per thread, stores method calls, local variables, primitive values, references (pointers to the objects inside the heap), memory automatically cleaned when method exits
	+ Heap: shared memory space between all threads, managed by garbage collector.
----



----
- Q13: did you work with streams before, if so, give me example two terminal and two intermidate operations
- Q13/Solution:
  + Intermidiate: (map, filter, flatMap, distict, sorted, skip)
  + Terminal: (forEach, collect, reduce, min, max, count, anyMatch)
----


----
- Q14: I have a list of orders, each order has list of items, each item has a category. given a list of orders, give me unique list of categories

```java

public class Item {
    private String category; // electronics, books, media, ...
}

import java.util.List;

public class Order {
    private List<Item> items;
}

public Set<String> getAllUniqueCategories(List<Order> orders) {
        return ???;
}

```

- Q14/Solution:
```java
public Set<String> getAllUniqueCategories(List<Order> orders) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(Item::getCategory)
                .collect(Collectors.toSet());
}


// Or also: 
public Set<String> getAllUniqueCategories(List<Order> orders) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(Item::getCategory)
                .collect(Collectors.toSet());
}
```
----



——————

## Spring 

----
- Q1: list me some design patterns that are implemented in the spring framework
- Q1/Solution: signleton, proxy, Iversion of Control (dependency injection is an implementaiton of IOC).
----


----
- Q2: Can you spot an issue with this code ?

````java
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentGateway paymentGateway;

    // Called by the controller
    public void checkout(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        completeOrder(order); 
    }

    @Transactional
    public void completeOrder(Order order) {
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        paymentGateway.charge(order.getAmount()); 
    }
}
````
- Q2/Solution: spring uses proxies to implement aop
	+ follow up: can you give me examples of other annotations where i might have the same problem ?  (@Cachable, @Async, @PreAuthorize("hasRole('some_shit')")  
----



----
- Q3: Given the following code, and the logs it produced, After I checked in the database, I found that the stock and last update fields did not update, could you explain why ?
	+ Follow up question: 

````java
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;

    @Transactional
    public void upsertAndAdjust(String sku, int extraStock) {
        log.info("upsertAndAdjust() started for sku={}", sku);

        Product product;
        try {
            product = productRepository.save(new Product(sku));
        } catch (PersistenceException e) {
            product = productRepository.findById(sku).orElseThrow();
        }

        product.setStock(product.getStock() + extraStock);
        product.setLastUpdated(Instant.now());

        log.info("upsertAndAdjust() completed successfully");
    }
}

// GENERATED LOGS 
// 09:15:22.001  INFO  ProductService - upsertAndAdjust() started for sku=ABC-123
// 09:15:22.062  INFO  ProductService - upsertAndAdjust() completed successfully
````

- Q3/Solution: Transaction is marked for rollback-only, a persistenceException is thrown, hibernate marked it as rollback only before you even caught the exception. The spring proxy decided to rollback the transaction because of this flag.
	+ follow up: if a transactional method exists because of an exception, does spring rollback on all exceptions ? only on PersistanceExceptions ?  (expect default behaviour, does not change for PersistanceException (not all of them, NoResult, lock timeout => ok))
----




## Database
----
- Q3: Explain the difference between optimistic and pessimistic locking
- Q3/Solution: in the answer look for: solve "lost update problem"  
	+ optimisitic: 
		+ update where version = ..  
		+ low contention
	+ Pessimistic: 
		+ select for update
		+ high contention, critcal 
		+ uses a database lock 
		+ can cause dead locks => set timeouts

----




## Angular 
- Q1: Directive vs Component
	+ A component is a directive with a view (html/css). it defines ui and logic. A directive is just behaviour that you can attach to existing view elements in the DOM. Example prebuilt directives : ngIf, ngFor, ngModel...

- Q2: How to pass data between components in angular ?
	+ @Input, @Output, or using a shared service for unrelated components


- Q3: Behavioural subject vs Subject 
	+ BehaviouralSubject: use it for state managemnt, must have initial value, when subscribed, you get the latest version, can get current version using .getVersion()
	+ Subject: use for notifications (only care about future updates): no initial value, when you subscribe, you only receive event when a new event is emitted, dont get notified about old ones. 

- Q4: What is the use of the async keyword ?
	+ its a pipe, used in ui of components, automatically subscribe to an Observable, and automatically unsubscribe when component is destroyed.

- Q5: what are route gards ? (canActivate)
	+ a function that determines if a user is allowed or not to visite a route in the application.


- Q6: which angular construct can you use to manage bearer token based authentication ?
	+ http interceptor



## Architecture
- Q1: You have a JMS listener consuming roughly 100k text messages per day. Each message is variable in size, up to 1MB. You need to detect and flag messages whose content is identical to one already seen. 
	- Constraints: 
		+ The listener is deployed across multiple servers, all consuming concurrently.
		+ You have no distributed cache (no Redis or similar).
		+ The only shared infrastructure available is an Oracle database.



