import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class OrderManagementSystem {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "order_management_data.json";
    
    // In-memory "collections"
    private Map<String, Customer> customers = new HashMap<>();
    private Map<String, Product> products = new HashMap<>();
    private Map<String, Order> orders = new HashMap<>();
    
    // Indexes for faster lookup
    private Map<String, Customer> emailToCustomer = new HashMap<>();
    private Map<String, Customer> phoneToCustomer = new HashMap<>();
    
    public static void main(String[] args) {
        OrderManagementSystem system = new OrderManagementSystem();
        system.loadData();
        
        // Sample usage
        system.addCustomer(new Customer("cust1", "John Doe", "john@example.com", "1234567890"));
        system.addCustomer(new Customer("cust2", "Jane Smith", "jane@example.com", "0987654321"));
        
        system.addProduct(new Product("prod1", "Laptop", 999.99));
        system.addProduct(new Product("prod2", "Phone", 699.99));
        system.addProduct(new Product("prod3", "Headphones", 149.99));
        
        // Create an order
        List<OrderItem> items = new ArrayList<>();
        items.add(new OrderItem(system.products.get("prod1"), 1));
        items.add(new OrderItem(system.products.get("prod3"), 2));
        system.addOrder(new Order("order1", system.customers.get("cust1"), items, new Date()));
        
        // Create another order
        items = new ArrayList<>();
        items.add(new OrderItem(system.products.get("prod2"), 1));
        system.addOrder(new Order("order2", system.customers.get("cust2"), items, new Date()));
        
        // Display all orders
        System.out.println("All Orders:");
        system.listAllOrders().forEach(System.out::println);
        
        // Find orders by customer email
        System.out.println("\nOrders for john@example.com:");
        system.findOrdersByCustomerEmail("john@example.com").forEach(System.out::println);
        
        // Update product price
        system.updateProductPrice("prod1", 899.99);
        System.out.println("\nAfter price update:");
        system.listAllOrders().forEach(System.out::println);
        
        // Find orders with total > 500
        System.out.println("\nOrders with total > 500:");
        system.findOrdersWhereTotalGreaterThan(500).forEach(System.out::println);
        
        // Generate invoice
        System.out.println("\nInvoice for order1:");
        System.out.println(system.generateOrderInvoice("order1"));
        
        // Delete customer and associated orders
        system.deleteCustomer("cust2");
        System.out.println("\nAfter deleting customer cust2:");
        system.listAllOrders().forEach(System.out::println);
        
        system.saveData();
    }
    
    // CRUD Operations
    
    public void addCustomer(Customer customer) {
        customers.put(customer.id, customer);
        emailToCustomer.put(customer.email, customer);
        phoneToCustomer.put(customer.phone, customer);
    }
    
    public void addProduct(Product product) {
        products.put(product.id, product);
    }
    
    public void addOrder(Order order) {
        // Calculate total amount
        double total = order.items.stream()
            .mapToDouble(item -> item.product.price * item.quantity)
            .sum();
        order.totalAmount = total;
        
        orders.put(order.id, order);
    }
    
    public List<Order> listAllOrders() {
        return new ArrayList<>(orders.values());
    }
    
    public List<Order> findOrdersByCustomerEmail(String email) {
        Customer customer = emailToCustomer.get(email);
        if (customer == null) return Collections.emptyList();
        
        return orders.values().stream()
            .filter(order -> order.customer.id.equals(customer.id))
            .collect(Collectors.toList());
    }
    
    public List<Order> findOrdersByCustomerPhone(String phone) {
        Customer customer = phoneToCustomer.get(phone);
        if (customer == null) return Collections.emptyList();
        
        return orders.values().stream()
            .filter(order -> order.customer.id.equals(customer.id))
            .collect(Collectors.toList());
    }
    
    public void updateProductPrice(String productId, double newPrice) {
        Product product = products.get(productId);
        if (product != null) {
            product.price = newPrice;
            
            // Update any existing orders that reference this product
            orders.values().forEach(order -> {
                order.items.forEach(item -> {
                    if (item.product.id.equals(productId)) {
                        item.subtotal = item.quantity * newPrice;
                    }
                });
                // Recalculate order total
                order.totalAmount = order.items.stream()
                    .mapToDouble(item -> item.product.price * item.quantity)
                    .sum();
            });
        }
    }
    
    public void deleteCustomer(String customerId) {
        Customer customer = customers.remove(customerId);
        if (customer != null) {
            emailToCustomer.remove(customer.email);
            phoneToCustomer.remove(customer.phone);
            
            // Delete all orders for this customer
            List<String> orderIdsToRemove = orders.values().stream()
                .filter(order -> order.customer.id.equals(customerId))
                .map(order -> order.id)
                .collect(Collectors.toList());
            
            orderIdsToRemove.forEach(orders::remove);
        }
    }
    
    // Bonus features
    
    public List<Order> findOrdersWhereTotalGreaterThan(double amount) {
        return orders.values().stream()
            .filter(order -> order.totalAmount > amount)
            .collect(Collectors.toList());
    }
    
    public String generateOrderInvoice(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) return "Order not found";
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder invoice = new StringBuilder();
        
        invoice.append("INVOICE\n");
        invoice.append("Order ID: ").append(order.id).append("\n");
        invoice.append("Date: ").append(dateFormat.format(order.orderDate)).append("\n\n");
        invoice.append("Customer:\n");
        invoice.append("  Name: ").append(order.customer.name).append("\n");
        invoice.append("  Email: ").append(order.customer.email).append("\n");
        invoice.append("  Phone: ").append(order.customer.phone).append("\n\n");
        invoice.append("Items:\n");
        
        for (OrderItem item : order.items) {
            invoice.append(String.format("  %-20s %3d x $%-8.2f $%.2f\n", 
                item.product.name, item.quantity, item.product.price, item.quantity * item.product.price));
        }
        
        invoice.append("\n");
        invoice.append(String.format("Total Amount: $%.2f", order.totalAmount));
        
        return invoice.toString();
    }
    
    // Data persistence
    
    private void saveData() {
        try (Writer writer = new FileWriter(DATA_FILE)) {
            Map<String, Object> data = new HashMap<>();
            data.put("customers", new ArrayList<>(customers.values()));
            data.put("products", new ArrayList<>(products.values()));
            data.put("orders", new ArrayList<>(orders.values()));
            
            gson.toJson(data, writer);
        } catch (IOException e) {
            System.err.println("Failed to save data: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            
            // Load customers
            List<Customer> customerList = gson.fromJson(gson.toJson(data.get("customers")), 
                new TypeToken<List<Customer>>() {}.getType());
            customerList.forEach(this::addCustomer);
            
            // Load products
            List<Product> productList = gson.fromJson(gson.toJson(data.get("products")), 
                new TypeToken<List<Product>>() {}.getType());
            productList.forEach(this::addProduct);
            
            // Load orders (need special handling for embedded documents)
            JsonArray ordersArray = (JsonArray) data.get("orders");
            for (JsonElement element : ordersArray) {
                JsonObject orderObj = element.getAsJsonObject();
                
                // Parse customer (embedded document)
                JsonObject customerObj = orderObj.get("customer").getAsJsonObject();
                Customer customer = gson.fromJson(customerObj, Customer.class);
                
                // Parse items
                List<OrderItem> items = new ArrayList<>();
                JsonArray itemsArray = orderObj.get("items").getAsJsonArray();
                for (JsonElement itemElement : itemsArray) {
                    JsonObject itemObj = itemElement.getAsJsonObject();
                    
                    // Parse product (embedded in OrderItem)
                    JsonObject productObj = itemObj.get("product").getAsJsonObject();
                    Product product = gson.fromJson(productObj, Product.class);
                    
                    int quantity = itemObj.get("quantity").getAsInt();
                    items.add(new OrderItem(product, quantity));
                }
                
                // Parse other fields
                String id = orderObj.get("id").getAsString();
                long orderDateMillis = orderObj.get("orderDate").getAsLong();
                Date orderDate = new Date(orderDateMillis);
                double totalAmount = orderObj.get("totalAmount").getAsDouble();
                
                // Reconstruct order
                Order order = new Order(id, customer, items, orderDate);
                order.totalAmount = totalAmount;
                orders.put(id, order);
            }
        } catch (IOException e) {
            System.err.println("Failed to load data: " + e.getMessage());
        }
    }
    
    // Entity classes
    
    static class Customer {
        String id;
        String name;
        String email;
        String phone;
        
        Customer(String id, String name, String email, String phone) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.phone = phone;
        }
        
        @Override
        public String toString() {
            return String.format("Customer[id=%s, name=%s, email=%s, phone=%s]", 
                id, name, email, phone);
        }
    }
    
    static class Product {
        String id;
        String name;
        double price;
        
        Product(String id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
        
        @Override
        public String toString() {
            return String.format("Product[id=%s, name=%s, price=%.2f]", id, name, price);
        }
    }
    
    static class Order {
        String id;
        Customer customer;
        List<OrderItem> items;
        Date orderDate;
        double totalAmount;
        
        Order(String id, Customer customer, List<OrderItem> items, Date orderDate) {
            this.id = id;
            this.customer = customer;
            this.items = items;
            this.orderDate = orderDate;
        }
        
        @Override
        public String toString() {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return String.format("Order[id=%s, customer=%s, date=%s, items=%d, total=%.2f]", 
                id, customer.name, dateFormat.format(orderDate), items.size(), totalAmount);
        }
    }
    
    static class OrderItem {
        Product product;
        int quantity;
        double subtotal;
        
        OrderItem(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
            this.subtotal = product.price * quantity;
        }
        
        @Override
        public String toString() {
            return String.format("OrderItem[product=%s, quantity=%d, subtotal=%.2f]", 
                product.name, quantity, subtotal);
        }
    }
}