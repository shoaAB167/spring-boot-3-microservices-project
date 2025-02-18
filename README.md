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
