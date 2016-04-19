# Embedded Kafka[![Build Status](https://travis-ci.org/tuplejump/embedded-kafka.svg)](http://travis-ci.org/tuplejump/embedded-kafka)
Embedded Kafka for demos, testing and quick prototyping. Much is taken right from the Apache Spark Streaming Kafka test files.

## Features

 - Quick and easy prototyping, testing 
 - Safe demos - not reliant on network
 - Compatible with Apache Kafka: 0.9.0.0, 0.9.0.1
 - Compatible with Scala 2.10 and 2.11
 - Embedded Zookeeper which starts automatically when starting Kafka
 - Simple Kafka consumer

## Roadmap

 - Improved and updated consumers
 - Validation
 - Additional and improved configuration
 - Documentation
 - And much more
  
## Scala Version
This project uses Scala 2.11 by default. To build against Scala 2.10 vs 2.11 run 
    
    sbt -Dscala.version=2.10.6 
    sbt -Dscala.version=2.10.6 test
    sbt -Dscala.version=2.10.6 publish-local
    
### Cross Build
Cross build both Scala versions by starting sbt then running these or other SBT tasks:
    
    + publish-local
    + test  

    