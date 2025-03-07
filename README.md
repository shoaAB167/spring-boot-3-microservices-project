# 🚀 Spring Boot 3 Microservices Project

## 📌 Services Overview

### 🛍️ Product Service
- **Database**: MongoDB
- **Start Database Docker Container**:
  ```sh
  cd my-product-service
  docker compose up -d
  ```

### 📦 Order Service
- **Database**: MySQL
- **Start Database Docker Container**:
  ```sh
  cd my-order-service
  docker compose up -d
  ```

### 📊 Inventory Service
- **Database**: MySQL Cluster (Shared with Order Service)
- **Start Database Docker Container** (only once):
  ```sh
  cd my-inventory-service
  docker compose up -d
  ```
- **Flyway for DB Migration**: Tracks all database changes.
- **Versioning Example**: `v2__add_inventory.sql` demonstrates Flyway versioning.

### 🔀 API Gateway
- **Port**: `9000`

## 📌 API Endpoints

### 📝 Order Management
#### ➕ Create Order
- **Method**: `POST`
- **URL**: `http://localhost:9000/api/order`
- **Payload**:
  ```json
  {
    "skuCode": "iphone_15",
    "price": 43000,
    "quantity": 3,
    "userDetails": {
      "email": "shaikhshoaib16799@gmail.com",
      "firstName": "Shoaib",
      "lastName": "Shaikh"
    }
  }
  ```

### 📦 Product Management
#### ➕ Add Product
- **Method**: `POST`
- **URL**: `http://localhost:9000/api/product`
- **Payload**:
  ```json
  {
    "name": "iphone 18",
    "description": "Apple 256 GB",
    "price": "75000"
  }
  ```

#### 📋 Get All Products
- **Method**: `GET`
- **URL**: `http://localhost:9000/api/product`

### 📊 Inventory Management
#### ➕ Add Inventory
- **Method**: `POST`
- **URL**: `http://localhost:9000/api/inventory`
- **Payload**:
  ```json
  {
    "skuCode": "macbook_pro",
    "quantity": 50
  }
  ```

#### 🔍 Get Inventory Status
- **Method**: `GET`
- **URL**: `http://localhost:9000/api/inventory?skuCode=iphone_15&quantity=100`

## 🔐 Keycloak Security
- **Authentication & Authorization**: Uses **Keycloak**.
- **Start Keycloak Docker Container**:
  ```sh
  cd api-gateway
  docker compose up -d
  ```
- **Keycloak UI**: [http://localhost:8181](http://localhost:8181)

### 🛠️ Setting Up Keycloak Client
1. **Create Client in Keycloak**
     client authentication => ON 
     service account roles enable other option disable
     Next => Save
     
2. **Obtain**:
   - Client ID
   - Secret Key
   - Authorization Endpoint URL (From OpenID Config in Realm Settings)
   - Grant Type: `client_credentials`

### 🔄 How Keycloak Works
1️⃣ **User Authentication** (OAuth2/OpenID Connect)
2️⃣ **Keycloak Validates Credentials & Generates Access Token (JWT)**
3️⃣ **User Redirected Back to Application**
4️⃣ **Application Uses Token for API Requests**

## 📑 API Documentation (Swagger UI)
- **Aggregated on API Gateway**
- **URL**: [http://localhost:9000/swagger-ui.html](http://localhost:9000/swagger-ui.html)
- **Dropdown to Select Services**

## ⚠️ Circuit Breaker Pattern
- Uses **Spring Boot Actuator** to monitor and manage applications.
- **Health Endpoint**: [http://localhost:9000/actuator/health](http://localhost:9000/actuator/health)

## 📡 Kafka Integration (Async Communication)
- **Between Order & Notification Service**
- **Kafka UI**: [http://localhost:8086](http://localhost:8086)
- **Schema Registry**: [http://localhost:8085](http://localhost:8085)
- **Message Format**: Avro Schema (`OrderRequestEvent`)
- **Steps to Work with Kafka**:
   1. Create Kafka Cluster
   2. Configure Broker Name & Port (in Docker Image)

## 📩 Notification Service
- **Sends Order Confirmation Message to Customers**
- **Consumes Kafka Messages from Order Service**
- **Uses**: [Mailtrap.io](https://mailtrap.io/) for email testing
- **Kafka Avro Deserializer**: For deserializing messages


## KAFKA UI Image to demonstrate the messages : 
![](C:\Shoaib\spring-boot-3-microservices-project\Project Images\Screenshot 2025-03-03 114939.png)

## Docker containers for running the project :
![](C:\Shoaib\spring-boot-3-microservices-project\Project Images\Screenshot 2025-03-03 100938.png)

## Mailtrap to send the notification of order confirmation to the user :
![](C:\Shoaib\spring-boot-3-microservices-project\Project Images\Screenshot 2025-03-03 114833.png)

## Observalibility
Used Grafana stack for observability i.e logging, determining metrics and tracing
1.Loki for loffing added loki tool image in api gateway docker compose file
Add logback-spring.xml file in src/main/resources for configuring the logs
Log URL : http://localhost:3100/loki/api/v1/push
