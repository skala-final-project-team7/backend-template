package com.lina.bff.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lina.admin.ingest.rabbitmq")
public record AdminIngestRabbitProperties(String ingestExchange, String ingestRoutingKey) {}
