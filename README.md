# spring-boot-3-microservices-project

## product service 
Database use for product service : MongoDB
To start database docker container 
go into my-product-service directory
run docker compose up -d

## order service
Database use for product service : MySQL
To start database docker container
go into my-order-service directory
run docker compose up -d

## inventory service
For order and inventory service we have use mysql cluster
In that cluster we have order_service and inventory_Service database
So we have to up docker container for that only once
For this service we have use flyway as database migration tool which help to track all database changes from start

## API gateway
Used Port : 9000

## Requests

1. Create order
Method : POST
URL : http://localhost:9000/api/order
Payload : 
{
   "skuCode" : "iphone_15",
   "price" : 60000,
   "quantity" : 1
}

2. Add product
Method : Post
URL : http://localhost:9000/api/product
Payload : 
{
   "name":"iphone 18",
   "description":"Apple 256 GB",
   "price":"75000"
}

3. Get all products
Method : Get 
URL : http://localhost:9000/api/product

4. Get inventory status
Method : Get
URL : http://localhost:9000/api/inventory?skuCode=iphone_15&quantity=100

## Keycloak security
For security endpoints we have used KeyClock which is open source tool for all types of authentication and authorization
Go into api-gateway directory and up keycloak docker container
use http://localhost:8181 to see keycloak UI

### For generating access token we need some keys to test API using postman
create client on keycloak
1. client Id, 
2. secret key 
3. authorization endpoint url as access token url (Go into RealM setting => OpenId endpoint configuration => select authorization_endpoint url)
4. select Grant Type as "client credential"

🚀 How Keycloak Works
1️⃣ User Authentication (OAuth2/OpenID Connect)
Users are redirected to Keycloak for login.
Keycloak validates credentials and generates an Access Token (JWT).
The user is redirected back to the application with a token.
The application uses the token for API requests.

## Swagger UI and open API
Here we have generated the swagger UI for each of the service and aggregated on 
api-gateway service
Url to view documentation : http://localhost:9000/swagger-ui.html
You can select any service from drop down of top right corner

## Integration of kafka for async communication between order and notification service
We have run docker image from order-service folder which contain images for kafka-broker, kafka-ui also the schema registry for kafka topic
Kafka ui url : http://localhost:8086
Schema registry URL to validate kafka topic : http://localhost:8085
Used avro to generate topic schema best for production code as producer and consumer uses same schema(OrderRequestEvent)
To work with kafka create cluster with broker name and the port which we have added in the docker image

